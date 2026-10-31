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
4. **Configurar o modo Dolby Vision separado** (passo 4): alguns apps, como o Apple TV,
   ignoram o ponto 2 e mandam Dolby Vision mesmo assim.
5. **Instalar o app [TCL Bypass](#app-reaplica-o-bloqueio-de-hdr-sozinho-a-cada-boot)**:
   o ponto 2 se desfaz a cada reinício da TV, e o app reaplica sozinho no boot.

Resumo do que fica configurado na TV testada:

| Onde | Configuração |
|---|---|
| Imagem (conteúdo normal) | Pré-definição `Padrão`, luz de fundo `10`, contraste dinâmico desligado, conversão automática de HDR desligada |
| Imagem (Dolby Vision) | Pré-definição `Filme escuro`, luz de fundo `10` |
| Android | HDR desativado (Dolby Vision, HDR10, HLG, HDR10+), reaplicado no boot pelo app |

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
adb connect IP_DA_TV:5555
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

## Passo 4: configurar o modo Dolby Vision (essencial)

O passo 3 só **avisa** aos apps que a tela não tem HDR. YouTube e Disney+ respeitam,
mas o **Apple TV ignora**: ele abre direto o decodificador Dolby Vision do chip
(`OMX.amlogic.dolby-vision.dvhe.decoder...`, visto no `logcat`). Em Dolby Vision a TCL
usa uma **configuração de imagem separada**, que vem de fábrica como **"Filme brilhante"**,
e o próprio Dolby Vision passa a controlar a luz de fundo. Os LEDs estouram e a tela fica preta.

Essa configuração **só aparece no menu enquanto um vídeo Dolby Vision está tocando**.

1. Dê play em algo em Dolby Vision (ex.: uma série no app Apple TV).
   Se a tela ficar preta, continue mesmo assim, porque o menu pode ser lido pelo adb.
2. Abra o menu de Imagem por cima do vídeo:

   ```bash
   adb shell am start -a android.settings.DISPLAY_SETTINGS
   ```

   (ou pelo controle: engrenagem → Imagem)
3. **Pré-definição de imagens:** `Filme escuro` (as opções são `Filme brilhante` e `Filme escuro`).
4. **Luz de fundo:** `10` (o padrão do "Filme escuro" é 19).
5. Volte e dê play. Se a tela continuar preta, o driver de LED travou na proteção:
   desligue a TV pelo controle, espere 10 s e ligue de novo.

A TV guarda isso para **todos** os vídeos Dolby Vision. Com a tela preta, dá para ler o
menu como texto (screenshots saem pretos por causa do DRM):

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -oE 'text="[^"]+"'
```

### O que não funciona (testado)

- **Brilho do Android** (`settings put system screen_brightness`): chega ao driver de luz
  (`dumpsys lights`), mas o Dolby Vision passa por cima.
- **Serviço de backlight da TCL** (`tcl_tv_display`, que tem `setBacklight` e
  `setBacklightConstant`): o SELinux esconde esse serviço do adb. Só funcionaria com root.
- **Menu de hotel** (`com.tcl.hotelmenu`): fecha ao ser aberto pelo adb.

---

## App: reaplica o bloqueio de HDR sozinho a cada boot

O bloqueio de HDR (passo 3) **se desfaz toda vez que a TV reinicia**. O app
[`app/tcl-bypass.apk`](app/tcl-bypass.apk) resolve isso sem depender de computador:

- Liga junto com a TV (`BOOT_COMPLETED`), espera 45 s a tela inicial carregar e
  roda o mesmo procedimento do `aplicar.sh`.
- Para isso ele se conecta ao **adb da própria TV** (`127.0.0.1:5555`) com um cliente
  ADB embutido. Assim ele tem as mesmas permissões do adb, sem root.
- Navega no menu escondido **lendo a tela** (`uiautomator`) e só aperta OK quando o item
  certo está focado. Passa direto por "Resolução", cuja prévia **derruba** o app de
  Configurações nesta TCL (`NoSuchMethodError` em `ResolutionSelectionFragment`).
- Confere `userDisabledHdrTypes [1, 2, 3, 4]` e tenta até 3 vezes.
- Tem uma tela com o status, o log e um botão **Aplicar agora**.

### Instalar

1. Faça os pré-requisitos (adb pela rede ligado).
2. Do computador:

   ```bash
   adb install app/tcl-bypass.apk
   ```

3. Na TV, abra o app **TCL Bypass** e aperte **Aplicar agora**.
4. Vai aparecer **"Permitir depuração USB?"** (é o próprio app pedindo acesso ao adb).
   Marque **Sempre permitir deste computador** e aperte **OK**. Só precisa fazer isso uma vez.
5. Pronto. A cada boot o status no app deve mostrar ✔.

Testado no TCL G09 (Android 12): após `adb reboot`, o display estava em `[]` e o app
deixou em `[1, 2, 3, 4]` cerca de 30 s depois da tela inicial aparecer.

### Compilar

Sem Gradle. Precisa de JDK 17 e do Android SDK (`platforms;android-34` e `build-tools;34.0.0`):

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
```

```bash
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platforms;android-34" "build-tools;34.0.0"
```

```bash
./app/build.sh
```

---

## Scripts

| Script | O que faz |
|---|---|
| [`scripts/aplicar.sh`](scripts/aplicar.sh) | Grava as configurações, abre o menu escondido, alterna Automático → Manual pelo adb e verifica |
| [`scripts/verificar.sh`](scripts/verificar.sh) | Mostra o estado atual das configurações e do display |
| [`scripts/reverter.sh`](scripts/reverter.sh) | Volta o HDR ao modo Automático (padrão de fábrica) |
| [`scripts/limpar-apps.sh`](scripts/limpar-apps.sh) | Remove apps de fábrica desnecessários (veja Extras) |

```bash
./scripts/aplicar.sh IP_DA_TV:5555
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
  no boot: o display volta para `userDisabledHdrTypes []`. O **app** resolve isso sozinho.
  Sem o app, rode `verificar.sh` depois de cada reinício completo (falta de luz, tirar da
  tomada, atualização) e, se der `ATENÇÃO`, rode o `aplicar.sh`. Colocar a TV em espera
  pelo controle não apaga a configuração. Os modos de imagem, as animações e os apps
  removidos **não** são afetados pelo reinício.
- **O app depende do adb pela rede ligado** nas Opções do desenvolvedor. Se desligar,
  ele para de funcionar (o status mostra ✘).
- **O IP da TV pode mudar** (o roteador entrega outro por DHCP). Se o `adb connect` falhar,
  veja o IP novo em Configurações → Rede. Para não acontecer de novo, reserve um IP fixo
  para a TV no roteador. Se o IP estiver certo e o `ping` responder, mas o adb disser
  `No route to host`, reinicie o servidor do adb: `adb kill-server && adb start-server`.
- **Atualização de firmware** da TCL pode mudar os menus. Se o app mostrar ✘ ou o
  `aplicar.sh` parar no meio, faça o passo 3.3 na mão.
- **O menu escondido "Tela e som" trava** se o cursor parar em "Resolução" (bug do firmware,
  `NoSuchMethodError` em `ResolutionSelectionFragment`). O app e o script passam direto por ali.
- **Screenshots saem pretos** durante vídeo com DRM (Apple TV, Netflix...). Para ler os menus
  nesse caso, use `uiautomator dump` (veja o passo 4).
- **Os ajustes de luz de fundo não são automatizados.** O menu de Imagem abre pelo adb
  (`am start -a android.settings.DISPLAY_SETTINGS`), mas cada modo (normal, Dolby Vision,
  HDR10) só aparece com aquele tipo de conteúdo tocando. Como esses valores ficam salvos,
  basta ajustar uma vez.

## Extras: limpeza e desempenho (opcional)

Não tem relação com a luz de fundo, mas deixa a TV (2 GB de RAM) mais leve. Tudo é reversível.

**Remover apps de fábrica** com [`scripts/limpar-apps.sh`](scripts/limpar-apps.sh)
(`pm uninstall --user 0`: o app sai da TV mas volta com um comando ou com um reset):

| Pacote | O que é |
|---|---|
| `com.tcl.channelplus` | TCL Channel (canais grátis com propaganda) |
| `com.tcl.waterfall.overseas` | Recomendações/propagandas da TCL |
| `com.tcl.tv.tclhome_passive` | Tela inicial da TCL (a TV usa a do Google TV) |
| `com.tcl.exhibit` / `com.tcl.esticker` | Modo demonstração e etiqueta de energia de loja |
| `com.tcl.ocean.instructions` / `com.tcl.repairguide` | Manual digital e guia de reparo |
| `com.tcl.messagebox` / `com.tcl.usercenter` | Central de mensagens e conta TCL |
| `com.tcl.logkit` / `com.tcl.gamebar` | Telemetria e Game Bar |
| `com.tcl.smartalexa` / `com.tcl.magiconnectfree` | Alexa e T-Cast |
| `com.google.android.play.games` / `com.google.android.youtube.tvmusic` | Play Games e YouTube Music |

```bash
./scripts/limpar-apps.sh IP_DA_TV:5555
```

Para trazer um de volta: `adb shell cmd package install-existing <pacote>`.

**Animações mais rápidas e limpar caches:**

```bash
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 0.5; done
```

```bash
adb shell pm trim-caches 999G
```

**Pelo controle remoto:** "Modo somente apps" (Configurações → Contas e login → sua conta)
deixa a tela inicial bem mais leve.

---

## Desfazer

```bash
./scripts/reverter.sh IP_DA_TV:5555
```

E volte a luz de fundo e os ajustes de imagem pelo menu da TV.
