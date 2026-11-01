import sys,os,subprocess
d=os.path.dirname(os.path.abspath(__file__))
head=open(os.path.join(d,'head.inc')).read()
sb='<div class="cam"></div><div class="sb"><span>9:41</span><span class="r"><i data-i="sig"></i><i data-i="wifi"></i><i data-i="bat"></i></span></div>'
names=sys.argv[1:]
for n in names:
    t=open(os.path.join(d,n+'.tpl')).read().replace('%%HEAD%%',head).replace('%%SB%%',sb)
    open(os.path.join(d,n+'.html'),'w').write(t)
subprocess.run(['node',os.path.join(d,'render.js')]+names,check=True)
