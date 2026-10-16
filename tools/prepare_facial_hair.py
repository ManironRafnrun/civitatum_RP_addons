"""Exact skin-UV pixel artwork, with editable SVG sources and generated RGBA textures."""
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/civitatum/textures/character/facial_hair'
SOURCE=ROOT/'art/facial_hair'


def texture(kind,number,front,chest):
    pixels={}
    def put(x,y,value,alpha=255):pixels[x,y]=(value,value,value,alpha)
    # Front of head (8x8); neither beard nor mustache overwrites the default eye row.
    for y,row in enumerate(front):
        for x,char in enumerate(row):
            if char!='.':put(8+x,8+y,int(char)*25+35,205 if kind=='beard' and number==1 else 255)
    if kind=='beard' and number!=3:
        for x in (7,16):
            for y in range(12,16):put(x,y,110+(x+y)%3*25)
        # Underside of the jaw, on the head bottom face.
        for y in range(1,7):
            for x in range(17,23):put(x,y,90+(x*3+y*7)%5*24)
    # Longer styles continue down the chest, like the long authored hairstyles.
    for y,row in enumerate(chest):
        for x,char in enumerate(row):
            if char!='.':put(20+x,20+y,int(char)*25+35)
    OUT.mkdir(parents=True,exist_ok=True);SOURCE.mkdir(parents=True,exist_ok=True)
    name=f'{kind}_{number}';image=Image.new('RGBA',(64,64))
    for point,color in pixels.items():image.putpixel(point,color)
    image.save(OUT/(name+'.png'))
    rectangles='\n'.join(f'<rect x="{x}" y="{y}" width="1" height="1" fill="#{r:02x}{g:02x}{b:02x}" opacity="{a/255:.6f}"/>' for (x,y),(r,g,b,a) in sorted(pixels.items()))
    (SOURCE/(name+'.svg')).write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64" shape-rendering="crispEdges">\n'+rectangles+'\n</svg>\n')


EMPTY=['........']*6
BEARDS=[
    (['2......2','243..342'],['..2323..']),
    (['34....43','34655463'],['.346643.','..4564..','..3453..','...34...']),
    (['...23...','..3443..'],['...34...','...23...']),
    (['34....43','34566543'],['..4564..','..3453..','...45...','...34...','...23...']),
    (['34....43','34566543'],['.345543.','..4564..','..3443..','...45...','...34...','...45...','...34...','...23...']),
]
for n,(front,chest) in enumerate(BEARDS,1):texture('beard',n,EMPTY+front,chest)
MUSTACHES=[['.234432.','...22...'],['.342243.','2......2'],['.23..32.','........'],['.345543.','.234432.'],['..2332..','........']]
for n,front in enumerate(MUSTACHES,1):texture('mustache',n,EMPTY+front,[])
print('Prepared five textured beards and five mustaches with SVG source art')
