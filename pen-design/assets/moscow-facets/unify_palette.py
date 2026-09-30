"""Build palette studies from the chosen master without changing its geometry."""
from pathlib import Path
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
im = Image.open(HERE.parent / 'generated-46.png').convert('RGB')
a = np.asarray(im, dtype=np.float32) / 255

def rgb(colors):
    return np.array([[int(c[i:i+2], 16)/255 for i in (0,2,4)] for c in colors.split()], dtype=np.float32)

anchors = rgb('203D49 285B65 548F8C 9ABBA5 F5E9BE EAB45F CA9341 E78254 955A40')
palettes = {
    'morning-unified': '284C59 427980 83B3AC C2D4BA F9EED4 EAC48A C39C62 DFA17E 99715C',
    'evening-unified': '222F40 344C5D 637B80 A1A191 F4CC9A ECA054 B86D3F D97150 824437',
    'night-unified': '101F31 17394A 295465 486E7B 7D989A AC915E 6C6451 8F6551 423D42',
}
d = ((a[:,:,None,:]-anchors[None,None,:,:])**2).sum(axis=-1)
w = np.exp(-(d-d.min(axis=-1,keepdims=True))/0.012)
w /= w.sum(axis=-1,keepdims=True)
base = w @ anchors
for name, colors in palettes.items():
    out = w @ rgb(colors) + (a-base)*0.65
    Image.fromarray(np.uint8(np.clip(out,0,1)*255)).save(HERE / (name+'.png'))
print('Wrote three palette studies; day uses the unmodified master.')
