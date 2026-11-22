"""Deterministic extraction from the user's approved sheet; no generated replacement artwork."""
from pathlib import Path
import json, math
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'Auralis Audiobook Icon Concept Sheet.png'
im=Image.open(source).convert('RGB')
res=ROOT/'app/src/main/res';out=ROOT/'branding/approved';out.mkdir(parents=True,exist_ok=True)
# Tight artwork regions exclude the sheet labels, original icon frames and cast shadows.
regions={'classic':(130,196,812,809),'signature':(949,229,1112,374),'horizon':(1207,243,1404,374),'pulse':(941,486,1113,651),'quill':(1207,477,1404,644)}
records={}
for name,box in regions.items():
 crop=im.crop(box);rgb=np.array(crop).astype(np.float32)
 # Source gold/bronze pixels retain their exact RGB values. The black presentation matte
 # becomes transparent; an edge ramp retains the original soft antialiasing and dark gold.
 chroma=rgb[:,:,0]-rgb[:,:,2]
 alpha=np.minimum(np.clip((rgb.max(2)-17)/25,0,1),np.clip((chroma-2)/7,0,1))
 if name=='classic':
  # Remove the lit presentation matte above the illustration; retain the source
  # planet's round silhouette and the complete book, headphone and ribbon artwork.
  silhouette=Image.new('L',crop.size);d=ImageDraw.Draw(silhouette)
  d.ellipse((36,5,682,639),fill=255);d.rectangle((0,425,crop.width,crop.height),fill=255)
  alpha*=np.array(silhouette)/255
 if name in ('classic','horizon'):
  # These two illustrations reach their presentation tile edges. Fade the removed
  # matte boundary, rather than carrying a rectangular patch into an adaptive icon.
  yy,xx=np.indices(alpha.shape);edge=np.minimum.reduce([xx,yy,alpha.shape[1]-1-xx,alpha.shape[0]-1-yy])
  ramp=np.clip(edge/(34 if name=='classic' else 15),0,1);alpha*=ramp*ramp*(3-2*ramp)
 alpha[alpha<.035]=0
 rgba=np.dstack([rgb,np.round(alpha*255)]).astype(np.uint8)
 fg=Image.fromarray(rgba);bounds=fg.getbbox();fg=fg.crop(bounds)
 # Each mark fits the guaranteed 66dp adaptive-icon safe circle in a 108dp canvas.
 a=np.array(fg.getchannel('A'));ys,xs=np.where(a>20);cx=(fg.width-1)/2;cy=(fg.height-1)/2
 radius=np.sqrt((xs-cx)**2+(ys-cy)**2).max();scale=128/radius # 32dp radius at 4x density
 resized=fg.resize((round(fg.width*scale),round(fg.height*scale)),Image.Resampling.LANCZOS)
 adaptive=Image.new('RGBA',(432,432));adaptive.alpha_composite(resized,((432-resized.width)//2,(432-resized.height)//2))
 adaptive.save(res/'drawable-nodpi'/f'ic_{name}_foreground.webp',lossless=True)
 # Standalone, tightly framed source artwork for inline marks and the opening animation.
 side=max(fg.size)+round(max(fg.size)*.12);mark=Image.new('RGBA',(side,side));mark.alpha_composite(fg,((side-fg.width)//2,(side-fg.height)//2))
 mark.resize((512,512),Image.Resampling.LANCZOS).save(res/'drawable-nodpi'/f'brand_{name}.webp',lossless=True)
 mark.save(out/f'{name}-extracted.png')
 # Trace the approved silhouette for themed icons and the actual monogram's stroke reveal.
 mask=(np.array(adaptive.getchannel('A'))>100).astype(np.uint8)*255
 mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((2,2),np.uint8))
 contours,hierarchy=cv2.findContours(mask,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)
 paths=[]
 for contour in sorted(contours,key=lambda c:cv2.boundingRect(c)[1]):
  if cv2.contourArea(contour)<5:continue
  points=cv2.approxPolyDP(contour,.6,True).reshape(-1,2)/4
  if len(points)<3:continue
  paths.append('M'+ ' L'.join(f'{x:.2f},{y:.2f}' for x,y in points)+' Z')
 path=' '.join(paths)
 vector=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{path}"/></vector>\n'
 (res/'drawable'/f'ic_{name}_monochrome.xml').write_text(vector)
 for version in ['v26','v33']:
  mono=f'<monochrome android:drawable="@drawable/ic_{name}_monochrome"/>' if version=='v33' else ''
  (res/f'mipmap-anydpi-{version}'/f'ic_launcher_{name}.xml').write_text(f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/brand_obsidian"/><foreground android:drawable="@drawable/ic_{name}_foreground"/>{mono}</adaptive-icon>\n')
 if name=='signature':
  # Tightly frame the same contours for drawing the approved monogram, not another letter A.
  trace=[]
  sigmask=(np.array(mark.getchannel('A'))>100).astype(np.uint8)*255
  cs,_=cv2.findContours(sigmask,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)
  for c in sorted(cs,key=lambda c:cv2.boundingRect(c)[1]):
   if cv2.contourArea(c)<4:continue
   points=cv2.approxPolyDP(c,.35,True).reshape(-1,2)/side*100
   trace.append('M'+' L'.join(f'{x:.3f},{y:.3f}' for x,y in points)+' Z')
  (res/'values'/'brand_trace.xml').write_text('<resources><string name="brand_signature_path" translatable="false">'+' '.join(trace)+'</string></resources>\n')
 records[name]={'sourceBox':box,'foregroundSize':resized.size,'sourceArtworkSize':fg.size}
# Review artwork: Android-style masks applied to the extracted adaptive layers.
board=Image.new('RGB',(1500,710),'#111113');draw=ImageDraw.Draw(board)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',19)
for i,name in enumerate(regions):
 for row,shape in enumerate(['circle','rounded']):
  x=40+i*295;y=38+row*325
  layer=Image.open(res/'drawable-nodpi'/f'ic_{name}_foreground.webp').resize((300,300),Image.Resampling.LANCZOS)
  # Adaptive viewport exposes the central 72dp, then the launcher applies its mask.
  tile=Image.new('RGBA',(300,300),'#111113');tile.alpha_composite(layer);tile=tile.crop((50,50,250,250)).resize((240,240),Image.Resampling.LANCZOS)
  mask=Image.new('L',(240,240));md=ImageDraw.Draw(mask)
  if shape=='circle':md.ellipse((0,0,239,239),fill=255)
  else:md.rounded_rectangle((0,0,239,239),radius=53,fill=255)
  bg=Image.new('RGB',(260,260),'#F5F0E6' if row else '#242428');bg.paste(tile,(10,10),mask);board.paste(bg,(x,y))
  draw.text((x+10,y+268),name.title(),fill='#F5BE70',font=font)
board.save(out/'adaptive-preview.png')
(out/'extraction.json').write_text(json.dumps(records,indent=2))
print(records)
