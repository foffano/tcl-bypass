# Gera res/drawable-xhdpi/banner.png (320x180) sem dependências.
import struct, zlib, sys
W, H = 320, 180
F = {  # fonte 5x7
 'T':["11111","00100","00100","00100","00100","00100","00100"],
 'C':["01111","10000","10000","10000","10000","10000","01111"],
 'L':["10000","10000","10000","10000","10000","10000","11111"],
 'H':["10001","10001","10001","11111","10001","10001","10001"],
 'D':["11110","10001","10001","10001","10001","10001","11110"],
 'R':["11110","10001","10001","11110","10100","10010","10001"],
 'O':["01110","10001","10001","10001","10001","10001","01110"],
 'F':["11111","10000","10000","11110","10000","10000","10000"],
 'B':["11110","10001","10001","11110","10001","10001","11110"],
 'Y':["10001","10001","01010","00100","00100","00100","00100"],
 'P':["11110","10001","10001","11110","10000","10000","10000"],
 'A':["01110","10001","10001","11111","10001","10001","10001"],
 'S':["01111","10000","10000","01110","00001","00001","11110"],
 ' ':["00000"]*7,
}
px = [[(24,26,32)]*W for _ in range(H)]
def text(s, y, scale, color):
    w = len(s)*6*scale - scale
    x0 = (W - w)//2
    for i,ch in enumerate(s):
        g = F[ch]
        for r in range(7):
            for c in range(5):
                if g[r][c]=='1':
                    for dy in range(scale):
                        for dx in range(scale):
                            px[y+r*scale+dy][x0+(i*6+c)*scale+dx] = color
text("TCL BYPASS", 40, 4, (255,255,255))
text("HDR OFF", 100, 5, (255,170,40))
raw = b''.join(b'\0'+bytes([v for p in row for v in p]) for row in px)
def chunk(t, d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
png = b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
open(sys.argv[1],'wb').write(png)
