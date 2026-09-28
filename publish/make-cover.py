# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, until an artist draws the real ones, in the style
# of Beltworks' publish/make-cover.py. Run from the repo root with Pillow installed,
# with the decompiled Minecraft sources (for the item textures) at ../mc-26.1.2.109-src.
from PIL import Image, ImageDraw, ImageFont
T='../mc-26.1.2.109-src/assets/minecraft/textures/'
BG=(24,26,32)
ACCENT=(80,200,190)
FADE=150  # 0 leaves the recipe rows at full strength, 255 hides them
# Real vanilla shapeless recipes: ingredients, result, result count.
# A repeated ingredient shows as one stack with its count.
RECIPES=[
    (['iron_ingot','flint'],'flint_and_steel'),
    (['blaze_powder','coal','gunpowder'],'fire_charge',3),
    (['paper','paper','paper','leather'],'book'),
    (['book','ink_sac','feather'],'writable_book'),
    (['ender_pearl','blaze_powder'],'ender_eye'),
    (['blaze_powder','slime_ball'],'magma_cream'),
    (['spider_eye','block/brown_mushroom','sugar'],'fermented_spider_eye'),
    (['block/brown_mushroom','block/red_mushroom','bowl'],'mushroom_stew'),
    (['green_dye','white_dye'],'lime_dye',2),
    (['red_dye','blue_dye'],'purple_dye',2),
    (['paper','gunpowder'],'firework_rocket',3),
    (['blaze_rod'],'blaze_powder',2),
    (['bone'],'bone_meal',3),
    (['sugar_cane'],'sugar'),
]
_cache={}
def tex(name,s):
    path=T+(name if '/' in name else 'item/'+name)+'.png'
    if (path,s) not in _cache:
        _cache[path,s]=Image.open(path).convert('RGBA').resize((s,s),Image.NEAREST)
    return _cache[path,s]
def slot(img,d,x,y,s,item,n=1):
    # a GUI slot: dark bevel top-left, light bevel bottom-right, item sprite inside
    b=max(2,s//12)
    d.rectangle([x,y,x+s-1,y+s-1],fill=(52,54,62))
    d.rectangle([x,y,x+s-1,y+b-1],fill=(30,31,37)); d.rectangle([x,y,x+b-1,y+s-1],fill=(30,31,37))
    d.rectangle([x,y+s-b,x+s-1,y+s-1],fill=(84,87,98)); d.rectangle([x+s-b,y,x+s-1,y+s-1],fill=(84,87,98))
    p=s//8; t=tex(item,s-2*p); img.paste(t,(x+p,y+p),t)
    if n>1:
        # stack count, bottom-right with a drop shadow, as in the inventory
        f=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf',s*9//20)
        bb=d.textbbox((0,0),str(n),font=f); tx=x+s-b-(bb[2]-bb[0])-bb[0]; ty=y+s-b-(bb[3]-bb[1])-bb[1]
        o=max(1,s//24); d.text((tx+o,ty+o),str(n),font=f,fill=(40,40,40)); d.text((tx,ty),str(n),font=f,fill=(255,255,255))
def recipe(img,d,x,y,s,r):
    # ingredient slots in a row, arrow, result slot
    ins,out,*c=r; g=s//8
    ins=list(dict.fromkeys((n,ins.count(n)) for n in ins))
    for i,(n,k) in enumerate(ins): slot(img,d,x+i*(s+g),y,s,n,k)
    ax=x+len(ins)*(s+g)+g; ay=y+s//2; aw=s
    d.rectangle([ax,ay-s//10,ax+aw*2//3,ay+s//10],fill=ACCENT)
    d.polygon([(ax+aw*2//3-2,ay-s//4),(ax+aw,ay),(ax+aw*2//3-2,ay+s//4)],fill=ACCENT)
    rx=ax+aw+g; slot(img,d,rx,y,s,out,c[0] if c else 1)
    return rx+s-x
def make(W,H,out,title_size,s):
    img=Image.new('RGBA',(W,H),BG+(255,)); d=ImageDraw.Draw(img)
    # rows fill the space above and below the title band, one more row runs behind it
    bh=int(title_size*1.6); free=(H-bh)//2; pitch=s+s//4
    n=free//pitch; top=(free-n*pitch+s//4)//2
    ys=[top+i*pitch for i in range(n)]+[(H-s)//2]+[H-free+top+i*pitch for i in range(n)]
    k=0
    for row,y in enumerate(ys):
        x=-((row*s*3)%(s*7))
        while x<W:
            x+=recipe(img,d,x,y,s,RECIPES[k%len(RECIPES)])+s; k+=5
    # fade the rows back so the title leads
    img=Image.alpha_composite(img,Image.new('RGBA',(W,H),BG+(FADE,)))
    band=Image.new('RGBA',(W,H),(0,0,0,0)); bd=ImageDraw.Draw(band)
    bd.rectangle([0,(H-bh)//2,W,(H+bh)//2],fill=(16,17,22,225))
    img=Image.alpha_composite(img,band)
    d=ImageDraw.Draw(img)
    lt=max(3,title_size//25)
    d.rectangle([0,(H-bh)//2,W,(H-bh)//2+lt],fill=ACCENT); d.rectangle([0,(H+bh)//2-lt,W,(H+bh)//2],fill=ACCENT)
    f1=ImageFont.truetype('/System/Library/Fonts/Supplemental/Impact.ttf',title_size)
    f2=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Black.ttf',int(title_size*0.62))
    a,w='CRAFT','works'
    b1=d.textbbox((0,0),a,font=f1); b2=d.textbbox((0,0),w,font=f2)
    w1=b1[2]-b1[0]; w2=b2[2]-b2[0]; h2=b2[3]-b2[1]
    px=int(title_size*0.16); gap=int(title_size*0.1)
    bw=w2+2*px; tot=w1+gap+bw; x=(W-tot)//2
    h1=b1[3]-b1[1]; y=(H-h1)//2-b1[1]; sh=max(3,title_size//20)
    d.text((x+sh-b1[0],y+sh),a,font=f1,fill=(0,0,0))
    d.text((x-b1[0],y),a,font=f1,fill=ACCENT)
    bx=x+w1+gap; bt=(H-h1)//2; bb=bt+h1
    d.rounded_rectangle([bx+sh,bt+sh,bx+bw+sh,bb+sh],radius=px,fill=(0,0,0))
    d.rounded_rectangle([bx,bt,bx+bw,bb],radius=px,fill=(240,240,236))
    ty=bt+(h1-h2)//2-b2[1]
    d.text((bx+px-b2[0],ty),w,font=f2,fill=BG)
    img.convert('RGB').save(out)
make(1280,640,'publish/craftworks-cover.png',150,80)
make(512,512,'publish/craftworks-icon.png',92,64)
