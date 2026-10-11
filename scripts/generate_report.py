"""Gera PDF de revisão técnica; não preenche identificação acadêmica."""
import re
from html import escape
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
ROOT=Path(__file__).resolve().parents[1]
fonts=Path('/usr/share/fonts/truetype/dejavu')
if (fonts/'DejaVuSans.ttf').exists():
    pdfmetrics.registerFont(TTFont('Body',str(fonts/'DejaVuSans.ttf')))
    pdfmetrics.registerFont(TTFont('Bold',str(fonts/'DejaVuSans-Bold.ttf')))
else:
    raise RuntimeError('Instale fontes DejaVu para preservar acentos no relatório.')
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='Text',fontName='Body',fontSize=10,leading=16,spaceAfter=7,alignment=TA_JUSTIFY))
styles.add(ParagraphStyle(name='TitleBR',fontName='Bold',fontSize=19,leading=25,spaceAfter=18))
styles.add(ParagraphStyle(name='HeadingBR',fontName='Bold',fontSize=13,leading=18,spaceBefore=12,spaceAfter=9))
styles.add(ParagraphStyle(name='CellBR',fontName='Body',fontSize=8,leading=11))

def inline(s):
    s=escape(s)
    return re.sub(r'`([^`]+)`',r'<font name="Body">\1</font>',s)

story=[Paragraph('RAÍZES DO NORDESTE',styles['TitleBR']),Paragraph('Projeto Multidisciplinar — Trilha Back-end',styles['HeadingBR']),
       Paragraph('Relatório técnico para estudo e revisão',styles['Text']),
       Paragraph('Identificação do estudante: não informada. Preencher e conferir as normas do documento oficial antes da entrega.',styles['Text']),
       Spacer(1,40),Paragraph('2026',styles['Text']),PageBreak()]
lines=(ROOT/'docs/relatorio.md').read_text().splitlines()
i=0
while i<len(lines):
    line=lines[i]
    if line.startswith('|'):
        rows=[]
        while i<len(lines) and lines[i].startswith('|'):
            l=lines[i];i+=1
            if re.match(r'^\|[\s|:-]+\|$',l):continue
            rows.append([Paragraph(inline(c.strip()),styles['CellBR']) for c in l.strip('|').split('|')])
        table=Table(rows,colWidths=[45,180,250],repeatRows=1,hAlign='LEFT')
        table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e6efe0')),('GRID',(0,0),(-1,-1),0.3,colors.grey),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),5),('RIGHTPADDING',(0,0),(-1,-1),5)]))
        story.extend([table,Spacer(1,10)]);continue
    if line.startswith('# '):
        i+=1;continue
    if line.startswith('##'):
        story.append(Paragraph(inline(line.lstrip('# ')),styles['HeadingBR']))
    elif line.strip():
        story.append(Paragraph(inline(line),styles['Text']))
    i+=1
for name,title in [('casos-de-uso','Casos de uso'),('classes','Classes conceituais'),('arquitetura','Arquitetura')]:
    story.extend([PageBreak(),Paragraph(title,styles['TitleBR'])])
    image=Image(str(ROOT/'docs/diagramas'/f'{name}.png'))
    scale=min(475/image.imageWidth,650/image.imageHeight)
    image.drawWidth=image.imageWidth*scale;image.drawHeight=image.imageHeight*scale
    story.append(image)
story.extend([PageBreak(),Paragraph('DER — modelo completo',styles['TitleBR']),
              Paragraph('O DER detalhado é fornecido também em SVG (ampliável), PNG e PDF A3 separado. Restrição UNIQUE/CHECK e índices constam na migration SQL. A tabela schema_migrations é metadado de infraestrutura.',styles['Text'])])
image=Image(str(ROOT/'docs/diagramas/der.png'))
scale=min(475/image.imageWidth,600/image.imageHeight)
image.drawWidth=image.imageWidth*scale;image.drawHeight=image.imageHeight*scale;story.append(image)

def footer(c,doc):
    c.setFont('Body',8);c.drawRightString(A4[0]-60,28,str(doc.page))
    c.drawString(60,28,'Raízes do Nordeste — estudo técnico')
SimpleDocTemplate(str(ROOT/'docs/relatorio-tecnico.pdf'),pagesize=A4,rightMargin=60,leftMargin=60,
                  topMargin=70,bottomMargin=60,title='Raízes do Nordeste — relatório técnico').build(story,onFirstPage=footer,onLaterPages=footer)
from reportlab.lib.pagesizes import A3,landscape
page=landscape(A3);c=canvas.Canvas(str(ROOT/'docs/diagramas/der.pdf'),pagesize=page)
c.setTitle('Raízes do Nordeste — DER');c.setFont('Bold',16);c.drawString(35,page[1]-35,'DER — Raízes do Nordeste')
c.drawImage(str(ROOT/'docs/diagramas/der.png'),35,35,width=page[0]-70,height=page[1]-95,preserveAspectRatio=True,anchor='c')
c.showPage();c.save()
print('Relatório técnico e DER PDF gerados.')
