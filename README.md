# KML Multi-Ferramenta

Aplicativo desktop em Java/Swing para edição e manipulação de arquivos **KML** e **KMZ** (Google Earth / Google Maps).

![Java](https://img.shields.io/badge/Java-8%2B-blue) ![License](https://img.shields.io/badge/license-MIT-green)

---

## Funcionalidades

### 🖊 Editor de Balão
- Carrega KML ou KMZ e exibe preview em HTML do primeiro Placemark.
- Injeta imagens com hiperlinks no balão de cada Placemark.
- Substitui palavras/valores nas descrições (múltiplos pares buscar → substituir).
- Aplica formatação automática com cores alternadas e bordas configuráveis, tanto para texto simples (chave=valor) quanto para tabelas HTML.

### 📍 Filtro por Estado
- Seleciona o estado brasileiro num combo e **busca automaticamente o polígono via [Nominatim / OpenStreetMap](https://nominatim.org/)** — sem coordenadas manuais.
- Suporte a todos os 27 estados + DF.
- Também aceita polígono manual (colagem de coordenadas `lon,lat`) ou carregado de outro KML/KMZ.
- Remove do arquivo de saída todos os Placemarks do tipo `<Point>` que estejam **fora** do polígono.

### 🎨 Editor de Estilos
- Lista todos os estilos de linha (`<LineStyle>`) do KML.
- Permite alterar a cor (color picker visual) e a espessura (slider 1–10) de cada estilo.
- Edita valores únicos de balão por chave (tag `<Data>` / `<displayName>`), diretamente em uma tabela.

---

## Requisitos

| Item | Versão mínima |
|------|--------------|
| Java | 11+ (usa `String.lines()`) |
| Conexão de rede | Necessária apenas para a busca Nominatim |

Não há dependências externas — usa apenas a biblioteca padrão Java SE.

---

## Como compilar e executar

```bash
# Compilar
javac KmlMultiTool.java

# Executar
java KmlMultiTool
```

Ou gerar um JAR executável:

```bash
javac KmlMultiTool.java
jar cfe KmlMultiTool.jar KmlMultiTool *.class
java -jar KmlMultiTool.jar
```

---

## Uso rápido

### Filtrar pontos por estado
1. Abra a aba **Filtro por Estado**.
2. Selecione o arquivo KML/KMZ de entrada e defina o arquivo de saída.
3. Escolha o estado no combo e clique em **Buscar Polígono via Nominatim**.
4. Aguarde o carregamento das coordenadas (barra de status mostra progresso).
5. Clique em **Filtrar e Salvar**.

### Formatar balões
1. Abra a aba **Editor de Balão**.
2. Carregue o arquivo de entrada — o preview do primeiro Placemark aparece automaticamente.
3. Adicione pares imagem/hiperlink se desejar.
4. Marque *"Aplicar nova formatação"* e configure as cores.
5. Clique em **Executar e Salvar**.

---

## Estrutura do projeto

```
KmlMultiTool.java       ← Arquivo único com todas as classes
README.md
LICENSE
```

O código foi mantido em **arquivo único** intencionalmente para facilitar distribuição e compilação sem ferramentas de build (Maven, Gradle etc.). Caso queira contribuir com módulos maiores, sinta-se à vontade para propor a separação em um PR.

---

## Notas técnicas

- **Formato de cor KML**: `AABBGGRR` (alpha, blue, green, red em hex) — diferente do padrão HTML `#RRGGBB`.
- **Nominatim**: a API exige um `User-Agent` identificável ([política de uso](https://operations.osmfoundation.org/policies/nominatim/)). O cabeçalho enviado é `KmlMultiTool/1.0`. Não faça requisições em loop — o polígono é buscado uma vez por estado.
- O parser de GeoJSON é manual (sem biblioteca), extraindo coordenadas via regex. Para polígonos com múltiplos anéis (MultiPolygon), é escolhido o anel externo mais longo.

---

## Licença

MIT — veja [LICENSE](LICENSE).

---

## Contribuindo

Issues e pull requests são bem-vindos!  
Se encontrar um estado cujo polígono não carrega corretamente, abra uma issue informando o nome do estado e a resposta recebida do Nominatim.
