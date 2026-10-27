# tcl-bypass

Paliativo para TVs TCL (Google TV / Android TV) com **luz de fundo (backlight) com defeito**,
em que a tela apaga quando o brilho sobe.

> ⚠️ Isto **não conserta** o defeito. Só evita que a TV peça mais corrente do que os LEDs
> danificados aguentam. A solução definitiva é trocar as barras de LED (ou a fonte/driver de LED).

---

## O problema

Sintoma típico:

- A TV liga, o **áudio continua funcionando**, mas a **imagem some** (tela preta).
- Com uma lanterna bem perto da tela, às vezes dá para ver a imagem bem fraca, ou seja,
  o painel está funcionando e **quem apagou foi a luz de fundo**.
- Acontece principalmente quando o brilho sobe muito: na hora de ligar, em conteúdo
  **HDR** (a TV joga a retroiluminação no máximo), em cenas muito claras, ou quando o
  "contraste dinâmico" ou o "sensor de luz" aumentam o brilho sozinhos.

Causa provável: barras de LED com algum LED em curto ou desgastado. Com corrente alta,
o driver de LED da TV detecta a falha e **desliga a retroiluminação por proteção**.
Com o brilho baixo, a corrente fica dentro do que os LEDs ainda aguentam e a TV
continua funcionando.

## A solução

Manter a luz de fundo **baixa e fixa o tempo todo**, sem nada que a aumente sozinho:

1. **Luz de fundo em 20%** em todo o sistema (feito pelo menu de Imagem da TV).
2. **HDR desativado** no Android, para os apps não mandarem vídeo HDR (o HDR faz a TCL
   aplicar presets `HDR10_BRIGHT` etc. e jogar o brilho no máximo).
3. **Desligar tudo que aumenta o brilho automaticamente** (contraste dinâmico, dimming,
   sensor de luz, modos "Smart HDR" ou IA).

O ponto 2 é o "bypass": na TCL a opção de HDR fica **escondida** e o comando
`settings put` sozinho **não basta**. Tudo está explicado abaixo.

---

## TV testada

| Item | Valor |
|---|---|
| Modelo | TCL Smart TV Pro (`G09`, 4K) |
| Build | `TCL/G09_4K_GB/G09:12/STT2.230203.001/AS24` |
| Android | 12 (SDK 31), Google TV |
| Painel | 3840×2160, HDR suportado: Dolby Vision, HDR10, HLG, HDR10+ |

Outros modelos TCL com Android TV 12 ou mais novo devem funcionar igual.

---

## Pré-requisitos

1. `adb` instalado no computador (macOS: `brew install android-platform-tools`).
2. Na TV: **Configurações → Sistema → Sobre → Build do sistema operacional**:
   aperte OK 7 vezes para liberar as **Opções do desenvolvedor**.
3. **Configurações → Sistema → Opções do desenvolvedor**: ative a **Depuração USB**
   e a **Depuração de rede/ADB pela rede**, se houver.
4. Descubra o IP da TV (Configurações → Rede) e conecte:

```bash
adb connect 192.168.1.16:5555
```

(Na primeira vez a TV mostra um pedido de autorização: aceite.)

Se a tela já estiver apagada, dá para fazer tudo com a TV "às cegas" pelo adb,
porque o áudio e o sistema continuam funcionando.

---

## Passo 1: luz de fundo em 20%

No controle remoto: **⚙ Configurações → Imagem**

- **Modo de imagem:** `Padrão` ou `Filme` (evite `Vívido`, `Smart HDR`, `Inteligente` e `IA`).
- **Luz de fundo / Retroiluminação:** `20`.
- Se a TV tiver **"Aplicar a todas as fontes"**, use para valer em HDMI, apps e TV.

> Se a tela estiver apagada e você não conseguir ver o menu, deixe uma lanterna bem
> perto da tela para enxergar o menu fraco.

## Passo 2: desligar o que aumenta o brilho sozinho

Ainda em **Imagem → Configurações avançadas** (os nomes variam conforme o modelo):

- **Contraste dinâmico** → desligado
- **Micro dimming / Local dimming** → desligado
- **Sensor de luz / Brilho automático** → desligado

E em **Configurações → Sistema → Energia**:

- **Economia de energia** → desligado (alguns modos mexem na retroiluminação)

## Passo 3: desativar o HDR (o bypass)

### 3.1 Gravar a configuração pelo adb

```bash
adb shell settings put global are_user_disabled_hdr_formats_allowed 0
```

```bash
adb shell settings put global user_disabled_hdr_formats 1,2,3,4
```

| Código | Formato |
|---|---|
| 1 | Dolby Vision |
| 2 | HDR10 |
| 3 | HLG |
| 4 | HDR10+ |

O nome da flag é invertido: `are_user_disabled_hdr_formats_allowed = 0` quer dizer
**modo Manual**, ou seja, **aplicar** a lista de formatos desativados. Com `1`
(modo Automático, o padrão), o Android ignora a lista.

### 3.2 Por que só o adb não funciona

Nesta TCL (Android 12), depois do `settings put` e até depois de reiniciar, o display
continua informando `userDisabledHdrTypes []`. O sistema **grava** a configuração,
mas **não aplica**. Só aplica quando a escolha é feita pela tela de configurações,
que chama a API do `DisplayManager` diretamente.

A TCL **escondeu** essa tela do menu normal, mas ela continua no sistema.

### 3.3 Abrir o menu escondido

```bash
adb shell am start -n com.android.tv.settings/.device.displaysound.DisplaySoundActivity
```

Na TV vai abrir a tela **"Tela e som"** do Android original. Navegue com o controle:

1. **Configurações de tela avançadas**
2. **Seleção de formato**
3. Selecione **Automático** e depois **Manual** de novo (isso força o sistema a aplicar).
4. Confira se **Dolby Vision**, **HDR10** e **HDR10+** estão **desligados**.

Ou faça tudo pelo adb, sem o controle remoto, com o script [`scripts/aplicar.sh`](scripts/aplicar.sh).

### 3.4 Verificar

```bash
adb shell dumpsys display | grep -oE "userDisabledHdrTypes \[[^]]*\]"
```

Resultado esperado (pelo menos a primeira linha, que é o `mBaseDisplayInfo`):

```
userDisabledHdrTypes [1, 2, 3, 4]
```

Se aparecer `[]`, repita o passo 3.3. Isso **sempre** acontece depois que a TV reinicia
(veja [Limitações](#limitações)).

---

## Scripts

| Script | O que faz |
|---|---|
| [`scripts/aplicar.sh`](scripts/aplicar.sh) | Grava as configurações, abre o menu escondido, alterna Automático → Manual pelo adb e verifica |
| [`scripts/verificar.sh`](scripts/verificar.sh) | Mostra o estado atual das configurações e do display |
| [`scripts/reverter.sh`](scripts/reverter.sh) | Volta o HDR ao modo Automático (padrão de fábrica) |

```bash
./scripts/aplicar.sh 192.168.1.16:5555
```

O `aplicar.sh` navega pelos menus simulando o controle remoto. Se a TCL mudar a ordem
dos itens numa atualização, a navegação pode errar; nesse caso faça o passo 3.3 na mão.

---

## Limitações

- **Não conserta o hardware.** Se os LEDs piorarem, 20% pode deixar de ser suficiente.
  Nesse caso, baixe ainda mais ou troque as barras de LED.
- **Reprodução "tunneled" (Netflix, Prime e outros):** alguns apps mandam o vídeo direto
  para o hardware da TV. Com o HDR desativado no Android eles devem escolher SDR, mas se
  algum vídeo ainda aparecer como HDR, abra o menu **Imagem** durante o vídeo, escolha o
  modo HDR **Usuário** e ajuste a luz de fundo para 20% também nesse modo.
- **O HDR volta depois que a TV reinicia.** As configurações continuam gravadas
  (`settings get` mostra `0` e `1,2,3,4`), mas nesta TCL o sistema **não reaplica** a lista
  no boot: o display volta para `userDisabledHdrTypes []`. Depois de cada reinício completo
  (falta de luz, tirar da tomada, atualização), rode `verificar.sh` e, se der `ATENÇÃO`,
  rode o `aplicar.sh` de novo. Colocar a TV em espera pelo controle não apaga a configuração.
  Animações e apps removidos **não** são afetados pelo reinício.
- **O IP da TV pode mudar** (o roteador entrega outro por DHCP). Se o `adb connect` falhar,
  veja o IP novo em Configurações → Rede. Para não acontecer de novo, reserve um IP fixo
  para a TV no roteador. Se o IP estiver certo e o `ping` responder, mas o adb disser
  `No route to host`, reinicie o servidor do adb: `adb kill-server && adb start-server`.
- **Atualização de firmware** da TCL pode mudar a ordem dos menus. Se o `aplicar.sh`
  parar no meio, faça o passo 3.3 na mão.
- O menu de **Imagem** da TCL (`com.tcl.settings.PictureActivity`) abre como uma camada
  sobre a tela que o adb não consegue ler. Por isso a luz de fundo precisa ser ajustada
  pelo controle remoto.

## Desfazer

```bash
./scripts/reverter.sh 192.168.1.16:5555
```

E volte a luz de fundo e os ajustes de imagem pelo menu da TV.
