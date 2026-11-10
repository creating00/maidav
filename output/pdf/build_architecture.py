from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor, white
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph, Table, TableStyle
from pypdf import PdfReader

OUT=Path(__file__).parent
pdfmetrics.registerFont(TTFont('Arial','C:/Windows/Fonts/arial.ttf'))
pdfmetrics.registerFont(TTFont('ArialB','C:/Windows/Fonts/arialbd.ttf'))
c=canvas.Canvas(str(OUT/'Maidav_Arquitectura.pdf'),pagesize=(595,842))
c.setTitle('Maidav: arquitectura y escalabilidad')
ink=HexColor('#193248'); light=HexColor('#eef4f7'); teal=HexColor('#007f78')
st=ParagraphStyle('p',fontName='Arial',fontSize=10,leading=14,textColor=ink)
sm=ParagraphStyle('s',parent=st,fontSize=9,leading=12)
md=['# Maidav: arquitectura y escalabilidad','','Revisado: 04/11/2026. Código local, commit d575b1a.','']
y=0
def p(s,style=st):
 global y
 a=Paragraph(escape(s),style); _,h=a.wrap(511,750); a.drawOn(c,42,y-h); y-=h+12
 md.extend([s,''])
def start(n,title,sub):
 global y
 c.setFillColor(ink);c.rect(0,730,595,112,fill=1,stroke=0)
 c.setFillColor(HexColor('#7fe0d0'));c.setFont('ArialB',9);c.drawString(42,812,'MAIDAV / MAPA DE ARQUITECTURA')
 c.setFillColor(white);c.setFont('ArialB',23);c.drawString(42,780,title)
 c.setFont('Arial',10);c.drawString(42,754,sub)
 c.setFillColor(ink);c.setFont('Arial',8);c.drawString(42,25,'04/11/2026 | commit d575b1a | Estado actual + propuesta de evolución')
 c.drawRightString(553,25,f'{n} / 5');y=705
 md.extend(['## '+title,'',sub,''])
def h(s):
 p(s,ParagraphStyle('h',parent=st,fontName='ArialB',fontSize=13,leading=17))
def table(headers,rows,widths):
 global y
 t=Table([[Paragraph(escape(v),sm) for v in r] for r in [headers]+rows],colWidths=widths)
 t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),HexColor('#d6e8ef')),('ROWBACKGROUNDS',(0,1),(-1,-1),[white,light]),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),9),('RIGHTPADDING',(0,0),(-1,-1),9),('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8)]))
 _,ht=t.wrap(511,750);t.drawOn(c,42,y-ht);y-=ht+15
 md.extend(['| '+' | '.join(headers)+' |','| '+' | '.join(['---']*len(headers))+' |']+['| '+' | '.join(r)+' |' for r in rows]+[''])
def box(x,top,w,height,title,desc):
 c.setFillColor(light);c.setStrokeColor(HexColor('#c0d6df'));c.roundRect(x,top-height,w,height,7,fill=1,stroke=1)
 a=Paragraph('<b>'+escape(title)+'</b><br/>'+escape(desc),sm);_,ht=a.wrap(w-18,height);a.drawOn(c,x+9,top-9-ht)
def arrow(x1,y1,x2,y2):
 import math
 c.setStrokeColor(teal);c.setLineWidth(1.4);c.line(x1,y1,x2,y2);a=math.atan2(y2-y1,x2-x1)
 for d in [-.45,.45]:c.line(x2,y2,x2-7*math.cos(a+d),y2-7*math.sin(a+d))
def end():
 assert y>45,f'Contenido fuera de página: {y}'
 c.showPage()

start(1,'Cómo está construido','Monolito web MVC organizado por capas técnicas y dominios.')
p('Maidav reúne interfaz, negocio y acceso a datos en una aplicación Java desplegable como una unidad. PostgreSQL y los archivos subidos conservan el estado persistente.')
top=y
layers=[('Navegador','HTML renderizado + interacciones JavaScript'),('Spring Security','Inicio de sesión, roles, permisos y filtros'),('Controladores MVC + Thymeleaf','Reciben solicitudes y preparan la respuesta HTML'),('Servicios de negocio','Ventas, crédito, catálogo y presupuestos'),('Repositorios Spring Data JPA','Consultas y persistencia de entidades'),('PostgreSQL','Datos y esquema versionado por Flyway')]
for i,(a,b) in enumerate(layers):
 t=top-i*62;box(123,t,349,47,a,b)
 if i<5:arrow(297,t-47,297,t-62)
y=top-375
md.extend(['```text','Navegador -> Seguridad -> Controlador -> Servicio -> Repositorio -> PostgreSQL','Controlador -> Thymeleaf -> HTML -> Navegador','Controladores de imágenes -> filesystem -> /uploads/**','```',''])
table(['Componente','Tecnología observada'],[
['Backend','Java 21; Spring Boot 3.5.9; Spring MVC, Security y Data JPA.'],
['Interfaz','Thymeleaf, recursos estáticos, Tailwind y Alpine.js por CDN; endpoints JSON puntuales.'],
['Datos / ejecución','PostgreSQL 16 en Compose; 53 migraciones Flyway (V1-V53); Maven y Docker.']],[110,401])
end()

start(2,'Módulos y relaciones','Todos comparten aplicación y base de datos.')
table(['Módulo','Responsabilidad','Ejemplos de código'],[
['Identidad','Usuarios, roles, permisos y acceso.','User, Role, Permission; UserServiceImpl.'],
['Clientes','Clientes y zonas.','Client, Zone; ClientServiceImpl.'],
['Catálogo','Productos, stock, proveedores, precios e imágenes.','ProductServiceImpl; ProductRepository.'],
['Ventas y crédito','Ventas, cuotas, cobros, mora y anulaciones.','SaleServiceImpl; CreditAccountServiceImpl.'],
['Presupuestos','Planes, cálculo y documentos PDF.','QuoteCalculator; QuoteServiceImpl.'],
['Administración','Dashboard, empresa y exportaciones.','DashboardPortfolioService; ExportDocumentService.']],[93,220,198])
h('Relaciones centrales, simplificadas')
top=y
box(42,top,145,52,'Cliente / vendedor','Participan en muchas ventas')
box(225,top,145,52,'Venta','Importes y forma de pago')
box(408,top,145,52,'Ítem de venta','Referencia un producto')
arrow(187,top-26,225,top-26);arrow(370,top-26,408,top-26)
box(225,top-80,145,52,'Cuenta de crédito','Cuenta con saldo y plan')
arrow(297,top-52,297,top-80)
box(42,top-160,215,49,'Cuotas','Vencimientos y estados por cuenta')
box(338,top-160,215,49,'Pagos','Cobros y reversiones por cuenta')
arrow(270,top-132,150,top-160);arrow(325,top-132,445,top-160)
y=top-227
md.extend(['```text','Cliente 1 -> N Venta; Vendedor 1 -> N Venta','Venta 1 -> N Ítem de venta; Producto 1 -> N Ítem de venta','Venta 1 -> 0..1 Cuenta de crédito (modelo JPA)','Cuenta de crédito 1 -> N Cuota; Cuenta de crédito 1 -> N Pago','```',''])
p('La venta financiada crea una cuenta y su calendario de cuotas. Los pagos se relacionan con la cuenta; el dibujo no representa una relación única directa entre pago y cuota.')
p('Carpetas: web/controller/web = HTTP; service/<dominio> = negocio; repository/<dominio> = datos; model/<dominio> = entidades; resources/templates = pantallas; db/migration = esquema.',sm)
end()

start(3,'Patrones que utiliza','Significado breve y evidencia concreta en el proyecto.')
table(['Patrón','Qué hace y dónde aparece'],[
['MVC + capas','SaleController recibe HTTP; Thymeleaf genera HTML; SaleServiceImpl coordina negocio. Algunos controladores también acceden a repositorios: la separación no es estricta.'],
['Service Layer','Servicios coordinan reglas. Es común la interfaz *Service con *ServiceImpl; también hay servicios y clases de apoyo concretas.'],
['Repository + ORM','JpaRepository encapsula consultas; @Entity mapea objetos a tablas. Hay JPQL y SQL nativo: existe acoplamiento con JPA/PostgreSQL.'],
['Inyección de dependencias','Spring conecta componentes mediante constructores; Lombok @RequiredArgsConstructor genera varios de ellos.'],
['Transacciones','@Transactional agrupa operaciones. Crear una venta incluye stock, cabecera, ítems y, si corresponde, cuenta y cuotas.'],
['Post/Redirect/Get','SaleController redirige después de guardar, reduciendo reenvíos al recargar una pantalla.'],
['Idempotencia','Crear una venta desde MVC usa UUID por intento y usuario. V52 define unicidad; el repositorio usa PESSIMISTIC_WRITE. Repetir la misma clave recupera la venta creada.'],
['RBAC + permisos','Spring Security y @PreAuthorize controlan acciones. AuthenticationRefreshFilter refresca autoridades por intervalo: PT5M por defecto.'],
['Auditoría y migraciones','BaseEntity registra creación/modificación con JPA Auditing. Flyway versiona el esquema. Esto no equivale a un historial completo de cada cambio.']],[120,391])
h('Qué arquitectura describe esto')
p('Es un monolito por capas con agrupación por dominio. Los dominios aún no son módulos aislados. No se observan puertos/adaptadores de arquitectura hexagonal ni servicios desplegables de forma independiente.')
end()

start(4,'Diseño para escalar','Propuesta de evolución: los componentes nuevos aún no están implementados.')
p('Mantener inicialmente el monolito y preparar varias instancias. Replicar la aplicación exige resolver sesiones y archivos compartidos, además de medir PostgreSQL.')
top=y
box(180,top,235,44,'Usuarios','Múltiples navegadores');arrow(297,top-44,297,top-60)
box(180,top-60,235,44,'Balanceador / proxy','Distribuye solicitudes y termina HTTPS')
arrow(267,top-104,160,top-129);arrow(327,top-104,435,top-129)
box(52,top-129,215,48,'Maidav A','Mismo código y versión');box(328,top-129,215,48,'Maidav B','Mismo código y versión')
arrow(160,top-177,160,top-203);arrow(435,top-177,435,top-203)
c.line(160,top-190,476,top-190);arrow(297,top-190,297,top-203);arrow(476,top-190,476,top-203)
box(42,top-203,155,63,'PostgreSQL compartido','Transacciones y límite global de conexiones')
box(220,top-203,155,63,'Sesiones compartidas','Evaluar Spring Session + Redis')
box(398,top-203,155,63,'Archivos compartidos','Storage de objetos o filesystem compartido')
y=top-286
md.extend(['```text','Usuarios -> Balanceador -> Maidav A / Maidav B','Las instancias comparten PostgreSQL, sesiones y archivos.','```',''])
table(['Dependencia actual','Cambio necesario para replicar'],[
['Sesión HTTP','Hay HttpSession y sincronización local en el filtro. Validar serialización y concurrencia con sesiones compartidas. La afinidad sola no resuelve caídas.'],
['Imágenes en disco','/uploads/** es público y depende del filesystem. Compartir almacenamiento, conservar URLs y definir acceso por tipo de archivo.'],
['Concurrencia comercial','El stock ya usa decremento condicional atómico. Conservarlo; probar cobros y anulaciones simultáneos. La idempotencia de ventas no cubre todos los flujos.'],
['Base de datos','Las réplicas multiplican conexiones. Medir consultas, bloqueos y pool total antes de sumar instancias.']],[120,391])
end()

start(5,'Ruta de crecimiento','Orden recomendado para evolucionar sin perder consistencia comercial.')
table(['Paso','Trabajo propuesto','Señal para avanzar'],[
['1. Medir y proteger','Medir latencia p95, errores, recursos y consultas. Automatizar pruebas críticas y verificar restauración de datos e imágenes.','Carga de referencia y pruebas de ventas, cobros, stock, permisos y reintentos.'],
['2. Optimizar','Revisar consultas lentas, cargas masivas y paginación. V53 ya agrega índices a ventas. Ajustar recursos según mediciones.','Meta de latencia y errores acordada, cumplida bajo carga representativa.'],
['3. Ordenar módulos','Delimitar catálogo, ventas, cobranza e identidad; extraer responsabilidades de clases grandes y mantener transacciones coherentes.','Cambiar un módulo con pruebas sin depender de detalles internos de otro.'],
['4. Replicar','Compartir sesiones y archivos; balanceador, observabilidad y despliegue controlado; coordinar migraciones Flyway.','Caída de una instancia sin pérdida de archivos ni duplicación de operaciones.'],
['5. Aislar cargas','Si las métricas lo justifican, pasar exportaciones pesadas a trabajos en segundo plano con reintentos e idempotencia.','Menor demora de pantallas y trazabilidad de cada trabajo.']],[65,255,191])
p('Microservicios: evaluarlos cuando un módulo necesite escalar o desplegarse de forma independiente y el equipo pueda operarlo. Separar ventas, stock y crédito agrega coordinación entre servicios; se recomienda mantenerlos juntos al principio.',sm)
h('Fuentes y alcance')
p('Código: pom.xml; src/main/java/com/sales/maidav/ (SaleController, SaleServiceImpl, ProductRepository, SaleIdempotencyKeyRepository, SecurityConfig, AuthenticationRefreshFilter, BaseEntity y entidades); src/main/resources/db/migration/; Dockerfile; docker-compose.yml; render.yaml.',sm)
p('Contexto: specs/current-system.md; adr/ADR-000-current-architecture.md; tasks/technical-debt.md. El código revisado actualiza el relevamiento previo: V1-V53, 15 archivos de prueba Java y una prueba de navegador; no se ejecutaron.',sm)
p('El Dockerfile construye omitiendo pruebas. No se detectó CI versionada ni almacenamiento distribuido de sesiones en las dependencias revisadas. Render y Compose describen despliegues posibles; no certifican el entorno productivo vigente.',sm)
p('Relevamiento estático: no se midió capacidad máxima ni rendimiento. La evolución es una propuesta; no se modificaron código ni infraestructura de la aplicación.',sm)
end()
c.save()
(OUT/'Maidav_Arquitectura.md').write_text('\n'.join(md),encoding='utf-8')
r=PdfReader(str(OUT/'Maidav_Arquitectura.pdf'));assert len(r.pages)==5
for i,page in enumerate(r.pages,1):
 t=page.extract_text();assert len(t)>500;print(f'Página {i}: {len(t)} caracteres')
print('Documentos creados:',OUT.resolve())
