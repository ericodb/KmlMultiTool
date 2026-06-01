// IMPORTS

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.CDATASection;
import org.w3c.dom.NodeList;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.List;

// CLASSES AUXILIARES (COMPARTILHADAS)

class ColorPreview extends JPanel {
    private Color color;

    public ColorPreview(String color_hex) {
        setPreferredSize(new Dimension(24, 24));
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        set_color_from_kml(color_hex);
    }

    public void set_color_from_kml(String kml_hex) {
        try {
            int alpha = Integer.parseInt(kml_hex.substring(0, 2), 16);
            int blue  = Integer.parseInt(kml_hex.substring(2, 4), 16);
            int green = Integer.parseInt(kml_hex.substring(4, 6), 16);
            int red   = Integer.parseInt(kml_hex.substring(6, 8), 16);
            this.color = new Color(red, green, blue, alpha);
        } catch (Exception e) {
            this.color = Color.BLACK;
        }
        repaint();
    }

    public void set_color(Color color) {
        this.color = color;
        repaint();
    }

    public Color getColor() {
        return this.color;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (color != null) {
            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}

class StyleListItem {
    String style_id;
    String text;
    String color;
    String width;
    Node   node;

    public StyleListItem(String style_id, String text, String color, String width, Node node) {
        this.style_id = style_id;
        this.text  = text;
        this.color = color;
        this.width = width;
        this.node  = node;
    }

    @Override
    public String toString() {
        return this.text;
    }
}

// CLASSE PRINCIPAL COM NAVEGAÇÃO

public class KmlMultiTool extends JDialog {

    private CardLayout cardLayout;
    private JPanel     container;

    private ImageEditorPanel imageEditorPanel;
    private StateFilterPanel stateFilterPanel;
    private StyleEditorPanel styleEditorPanel;

    // Botões de navegação — guarda referência para poder destacar o ativo
    private JButton btnPage1, btnPage2, btnPage3;

    public KmlMultiTool() {
        setTitle("KML Multi-Ferramenta");
        setModal(true);
        setSize(980, 780);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        container  = new JPanel(cardLayout);

        imageEditorPanel = new ImageEditorPanel();
        stateFilterPanel = new StateFilterPanel();
        styleEditorPanel = new StyleEditorPanel();

        container.add(imageEditorPanel, "page1");
        container.add(stateFilterPanel, "page2");
        container.add(styleEditorPanel, "page3");

        // ---- barra de navegação ----
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        navPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        btnPage1 = new JButton("🖊  Editor de Balão");
        btnPage2 = new JButton("📍  Filtro por Estado");
        btnPage3 = new JButton("🎨  Editor de Estilos");

        btnPage1.addActionListener(e -> mudarPagina("page1", btnPage1));
        btnPage2.addActionListener(e -> mudarPagina("page2", btnPage2));
        btnPage3.addActionListener(e -> mudarPagina("page3", btnPage3));

        navPanel.add(btnPage1);
        navPanel.add(btnPage2);
        navPanel.add(btnPage3);

        add(navPanel,  BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);

        mudarPagina("page1", btnPage1);
    }

    private void mudarPagina(String nomePagina, JButton ativo) {
        cardLayout.show(container, nomePagina);
        for (JButton b : new JButton[]{btnPage1, btnPage2, btnPage3}) {
            b.setFont(b.getFont().deriveFont(b == ativo ? Font.BOLD : Font.PLAIN));
        }
    }

    // ---- helper: linha de arquivo (label + campo + botão ícone) ----
    private static JPanel fileRow(String label, JTextField field, ActionListener browse) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(new EmptyBorder(2, 4, 2, 4));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(220, 24));
        JButton btn = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        btn.setToolTipText("Selecionar arquivo");
        btn.addActionListener(browse);
        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.add(btn,   BorderLayout.EAST);
        return row;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KmlMultiTool dialog = new KmlMultiTool();
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
        });
    }

// PÁGINA 1 — EDITOR DE BALÃO

    class ImageEditorPanel extends JPanel {

        private JTextField txtInput, txtOutput, txtSeparator;
        private JTextField txtBgColor1, txtBgColor2, txtBorderColor;
        private JCheckBox  checkFormat;
        private JPanel     imageLinkContainer, findReplaceContainer;
        private JEditorPane preview;
        private List<JTextField[]> imageLinkPairs   = new ArrayList<>();
        private List<JTextField[]> findReplacePairs = new ArrayList<>();
        private Document   doc;
        private List<Node> placemarks = new ArrayList<>();

        public ImageEditorPanel() {
            super(new BorderLayout(0, 4));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            // ---- painel superior ----
            JPanel panelTop = new JPanel();
            panelTop.setLayout(new BoxLayout(panelTop, BoxLayout.Y_AXIS));

            txtInput  = new JTextField();
            txtOutput = new JTextField();
            panelTop.add(fileRow("Arquivo de entrada (KML/KMZ):", txtInput,
                    e -> selecionarEntrada()));
            panelTop.add(fileRow("Arquivo de saída (KML/KMZ):",  txtOutput,
                    e -> selecionarSaida()));

            // ---- opções ----
            JPanel panelOption = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            checkFormat = new JCheckBox("Aplicar nova formatação com cores e bordas");
            panelOption.add(checkFormat);
            panelOption.add(new JLabel("Separador:"));
            txtSeparator = new JTextField("=", 3);
            panelOption.add(txtSeparator);
            panelTop.add(panelOption);

            // ---- cores ----
            JPanel panelColors = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            panelColors.add(new JLabel("Cores:"));
            txtBgColor1    = addColorPicker(panelColors, "Fundo 1", "#DDDDFF");
            txtBgColor2    = addColorPicker(panelColors, "Fundo 2", "#FFFFFF");
            txtBorderColor = addColorPicker(panelColors, "Borda",   "#000000");
            panelTop.add(panelColors);

            // ---- imagens / hyperlinks ----
            JPanel panelEditLeft = new JPanel();
            panelEditLeft.setLayout(new BoxLayout(panelEditLeft, BoxLayout.Y_AXIS));

            panelEditLeft.add(sectionHeader("Imagens e Hiperlinks:", e -> addImageLinkPair()));
            imageLinkContainer = new JPanel();
            imageLinkContainer.setLayout(new BoxLayout(imageLinkContainer, BoxLayout.Y_AXIS));
            addImageLinkPair();
            JScrollPane scrollImages = new JScrollPane(imageLinkContainer);
            scrollImages.setPreferredSize(new Dimension(460, 80));
            panelEditLeft.add(scrollImages);

            // ---- find / replace ----
            panelEditLeft.add(sectionHeader("Substituir Palavras na Descrição:", e -> addFindReplacePair()));
            findReplaceContainer = new JPanel();
            findReplaceContainer.setLayout(new BoxLayout(findReplaceContainer, BoxLayout.Y_AXIS));
            addFindReplacePair();
            JScrollPane scrollReplace = new JScrollPane(findReplaceContainer);
            scrollReplace.setPreferredSize(new Dimension(460, 180));
            panelEditLeft.add(scrollReplace);

            // ---- preview ----
            preview = new JEditorPane("text/html", "");
            preview.setEditable(false);
            JScrollPane scrollPreview = new JScrollPane(preview);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelEditLeft, scrollPreview);
            split.setDividerLocation(520);
            split.setResizeWeight(0.45);

            // ---- botões ----
            JPanel panelBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            JButton btnPreview = new JButton("🔍  Atualizar Preview");
            btnPreview.addActionListener(e -> atualizarPreview());
            JButton btnExec = new JButton("💾  Executar e Salvar");
            btnExec.addActionListener(e -> executar());
            panelBtns.add(btnPreview);
            panelBtns.add(btnExec);

            add(panelTop,   BorderLayout.NORTH);
            add(split,      BorderLayout.CENTER);
            add(panelBtns,  BorderLayout.SOUTH);
        }

        // ---- helper: cabeçalho de seção com botão "+" ----
        private JPanel sectionHeader(String title, ActionListener addAction) {
            JPanel h = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            h.add(new JLabel(title));
            JButton btn = new JButton("+");
            btn.setMargin(new Insets(0, 6, 0, 6));
            btn.addActionListener(addAction);
            h.add(btn);
            return h;
        }

        // ---- helper: adiciona um par label + campo de cor ao painel ----
        private JTextField addColorPicker(JPanel parent, String label, String defaultHex) {
            JTextField field = new JTextField(defaultHex, 7);
            JButton btn = new JButton(label);
            btn.addActionListener(e -> {
                Color c = JColorChooser.showDialog(this, "Escolher " + label,
                        parseHex(field.getText()));
                if (c != null) {
                    field.setText("#" + String.format("%06X", 0xFFFFFF & c.getRGB()));
                    atualizarPreview();
                }
            });
            parent.add(btn);
            parent.add(field);
            return field;
        }

        private Color parseHex(String hex) {
            try { return Color.decode(hex); } catch (Exception e) { return Color.WHITE; }
        }

        // ---- seleção de arquivos ----
        private void selecionarEntrada() {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Arquivo KML/KMZ de entrada");
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtInput.setText(ch.getSelectedFile().getAbsolutePath());
                carregarPreview(txtInput.getText());
            }
        }

        private void selecionarSaida() {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Arquivo KML/KMZ de saída");
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
                txtOutput.setText(ch.getSelectedFile().getAbsolutePath());
        }

        // ---- carregar / parse KML ----
        private void carregarPreview(String caminho) {
            try {
                doc = null;
                placemarks.clear();
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                if (caminho.toLowerCase().endsWith(".kmz")) {
                    try (ZipFile zf = new ZipFile(new File(caminho))) {
                        ZipEntry entry = zf.getEntry("doc.kml");
                        if (entry == null) entry = zf.getEntry("document.kml");
                        if (entry == null) throw new IOException("Entrada KML não encontrada no KMZ.");
                        try (InputStream is = zf.getInputStream(entry)) {
                            doc = dbf.newDocumentBuilder().parse(is);
                        }
                    }
                } else {
                    doc = dbf.newDocumentBuilder().parse(new File(caminho));
                }
                buscarPlacemarks(doc.getDocumentElement());
                if (!placemarks.isEmpty()) atualizarPreview();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao carregar arquivo:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void buscarPlacemarks(Node node) {
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("Placemark"))
                placemarks.add(node);
            Node child = node.getFirstChild();
            while (child != null) { buscarPlacemarks(child); child = child.getNextSibling(); }
        }

        // ---- preview ----
        private void atualizarPreview() {
            if (placemarks.isEmpty()) {
                preview.setText("<html><body><p>Nenhum Placemark encontrado ou arquivo não carregado.</p></body></html>");
                return;
            }
            Node pm = placemarks.get(0);
            Node nameNode = ((Element) pm).getElementsByTagName("name").item(0);
            String nameText = (nameNode != null) ? nameNode.getTextContent() : "";
            NodeList descNodes = ((Element) pm).getElementsByTagName("description");
            String descContent = (descNodes.getLength() > 0) ? descNodes.item(0).getTextContent() : "";

            for (JTextField[] p : findReplacePairs) {
                if (!p[0].getText().isEmpty())
                    descContent = descContent.replace(p[0].getText(), p[1].getText());
            }

            String finalContent = buildContent(descContent);
            String htmlToInject = buildImageHtml();
            preview.setText("<html><body><h3>" + nameText + "</h3>" + htmlToInject + finalContent + "</body></html>");
        }

        private String buildContent(String descContent) {
            if (!checkFormat.isSelected()) return descContent;
            String bg1 = txtBgColor1.getText(), bg2 = txtBgColor2.getText(), border = txtBorderColor.getText();
            return descContent.toLowerCase().contains("<table")
                    ? processarTabelaHtml(descContent, border, bg1, bg2)
                    : processarTextoPlano(descContent, txtSeparator.getText(), border, bg1, bg2);
        }

        private String buildImageHtml() {
            StringBuilder sb = new StringBuilder();
            for (JTextField[] p : imageLinkPairs) {
                if (!p[0].getText().isEmpty() && !p[1].getText().isEmpty())
                    sb.append("<span style=\"margin-right:10px;\"><a href=\"").append(p[1].getText())
                      .append("\"><img src=\"").append(p[0].getText())
                      .append("\" width=\"50\" height=\"50\"></a></span>");
            }
            return sb.toString();
        }

        private String processarTextoPlano(String texto, String sep, String border, String bg1, String bg2) {
            StringBuilder sb = new StringBuilder();
            sb.append("<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\" style=\"border:2px solid ")
              .append(border).append(";border-collapse:collapse;\">");
            int row = 0;
            for (String line : texto.split("\\r?\\n")) {
                line = line.trim();
                if (!sep.isEmpty() && line.contains(sep)) {
                    String[] parts = line.split(Pattern.quote(sep), 2);
                    String color = (row % 2 == 0) ? bg1 : bg2;
                    sb.append("<tr style=\"background-color:").append(color).append(";\"><td>")
                      .append(parts[0].trim()).append("</td><td>").append(parts[1].trim())
                      .append("</td></tr>");
                    row++;
                }
            }
            return sb.append("</table>").toString();
        }

        private String processarTabelaHtml(String html, String border, String bg1, String bg2) {
            Pattern rowPat  = Pattern.compile("<tr.*?>(.*?)</tr>",  Pattern.DOTALL);
            Pattern cellPat = Pattern.compile("<td.*?>(.*?)</td>", Pattern.DOTALL);
            StringBuilder sb = new StringBuilder();
            sb.append("<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\" style=\"border:2px solid ")
              .append(border).append(";border-collapse:collapse;\">");
            int row = 0;
            Matcher rm = rowPat.matcher(html);
            while (rm.find()) {
                Matcher cm = cellPat.matcher(rm.group(1));
                List<String> cells = new ArrayList<>();
                while (cm.find()) cells.add(cm.group(1));
                if (cells.size() >= 2) {
                    String color = (row % 2 == 0) ? bg1 : bg2;
                    sb.append("<tr style=\"background-color:").append(color).append(";\"><td>")
                      .append(cells.get(0).trim()).append("</td><td>").append(cells.get(1).trim())
                      .append("</td></tr>");
                    row++;
                }
            }
            return sb.append("</table>").toString();
        }

        // ---- pares dinâmicos ----
        private void addImageLinkPair() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            JTextField imgF = new JTextField(18), linkF = new JTextField(18);
            row.add(new JLabel("URL da Imagem:")); row.add(imgF);
            row.add(new JLabel("Hiperlink:"));     row.add(linkF);
            JButton rem = new JButton("−");
            rem.setMargin(new Insets(0, 5, 0, 5));
            JTextField[] pair = {imgF, linkF};
            rem.addActionListener(e -> { imageLinkContainer.remove(row); imageLinkPairs.remove(pair);
                                         imageLinkContainer.revalidate(); imageLinkContainer.repaint(); });
            row.add(rem);
            imageLinkPairs.add(pair);
            imageLinkContainer.add(row);
            imageLinkContainer.revalidate(); imageLinkContainer.repaint();
        }

        private void addFindReplacePair() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            JTextField findF = new JTextField(14), replF = new JTextField(14);
            row.add(new JLabel("Buscar:"));          row.add(findF);
            row.add(new JLabel("Substituir por:")); row.add(replF);
            JButton rem = new JButton("−");
            rem.setMargin(new Insets(0, 5, 0, 5));
            JTextField[] pair = {findF, replF};
            rem.addActionListener(e -> { findReplaceContainer.remove(row); findReplacePairs.remove(pair);
                                         findReplaceContainer.revalidate(); findReplaceContainer.repaint(); });
            row.add(rem);
            findReplacePairs.add(pair);
            findReplaceContainer.add(row);
            findReplaceContainer.revalidate(); findReplaceContainer.repaint();
        }

        // ---- executar ----
        private void executar() {
            String saida = txtOutput.getText();
            if (doc == null || placemarks.isEmpty() || saida.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Preencha todos os campos e carregue um arquivo válido.",
                        "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sep = txtSeparator.getText();
            if (sep.isEmpty()) {
                JOptionPane.showMessageDialog(this, "O campo Separador não pode estar vazio.",
                        "Atenção", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                String balloonTemplate = "<div style='margin-bottom:10px;'><h3>$[name]</h3>"
                        + buildImageHtml() + "</div>$[description]";

                for (Node pm : placemarks) {
                    NodeList descNodes = ((Element) pm).getElementsByTagName("description");
                    String descContent = (descNodes.getLength() > 0) ? descNodes.item(0).getTextContent() : "";

                    for (JTextField[] p : findReplacePairs)
                        if (!p[0].getText().isEmpty())
                            descContent = descContent.replace(p[0].getText(), p[1].getText());

                    String finalHtml = buildContent(descContent);

                    // BalloonStyle
                    Node styleNode = resolveStyleNode(pm);
                    NodeList existing = ((Element) styleNode).getElementsByTagName("BalloonStyle");
                    if (existing.getLength() > 0) styleNode.removeChild(existing.item(0));
                    Element balloonStyleNode = doc.createElement("BalloonStyle");
                    Element textEl = doc.createElement("text");
                    textEl.appendChild(doc.createCDATASection(balloonTemplate));
                    balloonStyleNode.appendChild(textEl);
                    styleNode.appendChild(balloonStyleNode);

                    // description
                    NodeList descEls = ((Element) pm).getElementsByTagName("description");
                    Element descEl;
                    if (descEls.getLength() > 0) {
                        descEl = (Element) descEls.item(0);
                        while (descEl.hasChildNodes()) descEl.removeChild(descEl.getFirstChild());
                    } else {
                        descEl = doc.createElement("description");
                        pm.appendChild(descEl);
                    }
                    descEl.appendChild(doc.createCDATASection(finalHtml));
                }

                salvarDoc(doc, saida);
                JOptionPane.showMessageDialog(this,
                        "KML/KMZ atualizado com sucesso!\nSalvo em: " + saida,
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao processar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private Node resolveStyleNode(Node pm) {
            Node styleUrlNode = ((Element) pm).getElementsByTagName("styleUrl").item(0);
            if (styleUrlNode != null) {
                String id = styleUrlNode.getTextContent().trim();
                if (id.startsWith("#")) id = id.substring(1);
                NodeList styles = doc.getElementsByTagName("Style");
                for (int i = 0; i < styles.getLength(); i++) {
                    Element s = (Element) styles.item(i);
                    if (s.getAttribute("id").equals(id)) return s;
                }
            }
            Node inline = ((Element) pm).getElementsByTagName("Style").item(0);
            if (inline != null) return inline;
            Element newStyle = doc.createElement("Style");
            pm.appendChild(newStyle);
            return newStyle;
        }
    }

// PÁGINA 2 — FILTRO POR ESTADO  (Nominatim / OpenStreetMap)

    class StateFilterPanel extends JPanel {

        private JTextField txtInput, txtOutput;
        private JTextArea  txtPolygon;
        private JComboBox<String> comboEstado;
        private JButton    btnBuscar, btnProcessar;
        private JLabel     statusLabel;
        private Document   doc;

        // Estados brasileiros — nome exato para o Nominatim
        private static final String[] ESTADOS_BR = {
            "Acre", "Alagoas", "Amapá", "Amazonas", "Bahia",
            "Ceará", "Distrito Federal", "Espírito Santo", "Goiás",
            "Maranhão", "Mato Grosso", "Mato Grosso do Sul", "Minas Gerais",
            "Pará", "Paraíba", "Paraná", "Pernambuco", "Piauí",
            "Rio de Janeiro", "Rio Grande do Norte", "Rio Grande do Sul",
            "Rondônia", "Roraima", "Santa Catarina", "São Paulo",
            "Sergipe", "Tocantins"
        };

        public StateFilterPanel() {
            super(new BorderLayout(0, 6));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            // ---- painel de arquivos ----
            JPanel panelFile = new JPanel();
            panelFile.setLayout(new BoxLayout(panelFile, BoxLayout.Y_AXIS));
            txtInput  = new JTextField();
            txtOutput = new JTextField();
            panelFile.add(fileRow("Arquivo de entrada (KML/KMZ):", txtInput,  e -> selecionarEntrada()));
            panelFile.add(fileRow("Arquivo de saída (KML/KMZ):",  txtOutput, e -> selecionarSaida()));
            add(panelFile, BorderLayout.NORTH);

            // ---- painel central (split: selector esquerdo / textarea direito) ----
            JPanel selectorPanel = buildSelectorPanel();
            JPanel polygonPanel  = buildPolygonPanel();

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, selectorPanel, polygonPanel);
            split.setDividerLocation(280);
            split.setResizeWeight(0.25);
            add(split, BorderLayout.CENTER);

            // ---- rodapé ----
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            statusLabel = new JLabel(" ");
            statusLabel.setForeground(new Color(0, 100, 0));
            btnProcessar = new JButton("▶  Filtrar e Salvar");
            btnProcessar.addActionListener(e -> processarEFiltrar());
            footer.add(btnProcessar);
            footer.add(statusLabel);
            add(footer, BorderLayout.SOUTH);
        }

        // ---- painel de seleção de estado ----
        private JPanel buildSelectorPanel() {
            JPanel p = new JPanel(new BorderLayout(0, 6));
            p.setBorder(BorderFactory.createTitledBorder("Buscar Polígono de Estado (Nominatim / OSM)"));

            JPanel inner = new JPanel();
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(new EmptyBorder(4, 6, 4, 6));

            inner.add(new JLabel("Estado:"));
            inner.add(Box.createVerticalStrut(4));

            comboEstado = new JComboBox<>(ESTADOS_BR);
            comboEstado.setSelectedItem("Santa Catarina");
            comboEstado.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            inner.add(comboEstado);

            inner.add(Box.createVerticalStrut(8));

            btnBuscar = new JButton("🌐  Buscar Polígono via Nominatim");
            btnBuscar.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnBuscar.addActionListener(e -> buscarPoligonoNominatim());
            inner.add(btnBuscar);

            inner.add(Box.createVerticalStrut(6));

            JLabel info = new JLabel("<html><small>Requer conexão com a internet.<br>"
                    + "Fonte: OpenStreetMap / Nominatim.</small></html>");
            info.setForeground(Color.GRAY);
            inner.add(info);

            inner.add(Box.createVerticalStrut(12));
            JSeparator sep = new JSeparator();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
            inner.add(sep);
            inner.add(Box.createVerticalStrut(8));

            JButton btnCarregar = new JButton("📂  Carregar de KML/KMZ...");
            btnCarregar.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnCarregar.addActionListener(e -> carregarPoligonoKML());
            inner.add(btnCarregar);

            p.add(inner, BorderLayout.NORTH);
            return p;
        }

        // ---- painel com textarea de coordenadas ----
        private JPanel buildPolygonPanel() {
            JPanel p = new JPanel(new BorderLayout(0, 4));
            p.setBorder(BorderFactory.createTitledBorder("Coordenadas do Polígono (lon,lat — uma por linha)"));

            txtPolygon = new JTextArea();
            txtPolygon.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            txtPolygon.setLineWrap(false);
            JScrollPane scroll = new JScrollPane(txtPolygon);
            p.add(scroll, BorderLayout.CENTER);

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
            JButton btnClear = new JButton("Limpar");
            btnClear.addActionListener(e -> txtPolygon.setText(""));
            JLabel countLabel = new JLabel("0 pontos");
            txtPolygon.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCount(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCount(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCount(); }
                void updateCount() {
                    long n = txtPolygon.getText().lines().filter(l -> !l.isBlank()).count();
                    countLabel.setText(n + " pontos");
                }
            });
            btnRow.add(countLabel);
            btnRow.add(btnClear);
            p.add(btnRow, BorderLayout.SOUTH);
            return p;
        }

        // ---- Nominatim ----
        private void buscarPoligonoNominatim() {
            String estado = (String) comboEstado.getSelectedItem();
            if (estado == null) return;

            btnBuscar.setEnabled(false);
            statusLabel.setText("Buscando polígono de " + estado + "...");

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    return fetchPolygonFromNominatim(estado);
                }

                @Override
                protected void done() {
                    btnBuscar.setEnabled(true);
                    try {
                        String result = get();
                        if (result != null && !result.isEmpty()) {
                            // Aplicar simplificação de RDP aqui para performance!
                            String simplificado = simplificarTextoPoligono(result, 0.005);
                            
                            txtPolygon.setText(simplificado);
                            long nOriginais = result.lines().filter(l -> !l.isBlank()).count();
                            long nSimplificados = simplificado.lines().filter(l -> !l.isBlank()).count();
                            
                            statusLabel.setText("✅ Polígono de " + estado + " carregado (" + nOriginais + " reduzidos para " + nSimplificados + " pts).");
                        } else {
                            statusLabel.setText("⚠ Nenhum polígono retornado para " + estado + ".");
                        }
                    } catch (Exception ex) {
                        statusLabel.setText("❌ Erro: " + ex.getMessage());
                        JOptionPane.showMessageDialog(StateFilterPanel.this,
                                "Erro ao buscar polígono:\n" + ex.getMessage(),
                                "Erro de rede", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }

        /**
         * Consulta o Nominatim para obter o polígono do estado.
         * Usa a API /search com polygon_geojson=1.
         * Retorna as coordenadas no formato "lon,lat\n" prontas para o textarea.
         */
        private String fetchPolygonFromNominatim(String estado) throws Exception {
            // Nominatim espera: state=<nome>, country=Brazil, polygon_geojson=1
            String query = URLEncoder.encode(estado + ", Brazil", StandardCharsets.UTF_8.name());
            String urlStr = "https://nominatim.openstreetmap.org/search"
                    + "?q=" + query
                    + "&featuretype=state"
                    + "&polygon_geojson=1"
                    + "&format=geojson"
                    + "&limit=1";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // User-Agent é obrigatório pela política do Nominatim
            conn.setRequestProperty("User-Agent", "KmlMultiTool/1.0 (github.com/ericodb/KmlMultiTool)");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            int status = conn.getResponseCode();
            if (status != 200)
                throw new IOException("Resposta HTTP " + status + " do Nominatim.");

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            return parseGeoJsonPolygon(sb.toString());
        }

        /**
         * Parse manual do GeoJSON retornado pelo Nominatim.
         * Extrai coordenadas de Polygon ou MultiPolygon (anel externo do maior anel).
         * Evita dependência de biblioteca JSON.
         */
        private String parseGeoJsonPolygon(String geojson) throws Exception {
            // Localiza o array de coordenadas do primeiro Polygon
            // Estrutura esperada: "coordinates":[[[lon,lat],...]]  (Polygon)
            // ou "coordinates":[[[[lon,lat],...],...]]             (MultiPolygon)
            // Estratégia: encontrar o bloco mais longo de pares [lon,lat]

            Pattern coordPairPat = Pattern.compile("\\[(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\]");

            // Divide em "anéis" pelo padrão [[[ ... ]]]
            Pattern ringPat = Pattern.compile("\\[\\[\\[(.+?)\\]\\]\\]", Pattern.DOTALL);
            Matcher rm = ringPat.matcher(geojson);

            List<String> rings = new ArrayList<>();
            while (rm.find()) rings.add(rm.group(1));

            if (rings.isEmpty()) {
                // Tenta Polygon simples [[...]]
                Pattern polyPat = Pattern.compile("\\[\\[(.+?)\\]\\]", Pattern.DOTALL);
                Matcher pm = polyPat.matcher(geojson);
                if (pm.find()) rings.add(pm.group(1));
            }

            if (rings.isEmpty())
                throw new IOException("Não foi possível extrair coordenadas do GeoJSON.");

            // Escolhe o anel com mais coordenadas (geralmente o anel externo do maior polígono)
            String bestRing = rings.stream()
                    .max(java.util.Comparator.comparingInt(String::length))
                    .orElseThrow();

            Matcher cm = coordPairPat.matcher(bestRing);
            StringBuilder result = new StringBuilder();
            while (cm.find()) {
                result.append(cm.group(1)).append(",").append(cm.group(2)).append("\n");
            }

            if (result.length() == 0)
                throw new IOException("Nenhum par de coordenadas encontrado no GeoJSON.");

            return result.toString().trim();
        }

        // ---- carregar polígono de KML/KMZ ----
        private void carregarPoligonoKML() {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("KML/KMZ com polígono");
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                Document polygonDoc;
                File file = ch.getSelectedFile();
                if (file.getName().toLowerCase().endsWith(".kmz")) {
                    try (ZipFile zf = new ZipFile(file)) {
                        ZipEntry entry = zf.getEntry("doc.kml");
                        if (entry == null) entry = zf.getEntry("document.kml");
                        if (entry == null) throw new IOException("KMZ sem 'doc.kml'.");
                        try (InputStream is = zf.getInputStream(entry)) {
                            polygonDoc = dbf.newDocumentBuilder().parse(is);
                        }
                    }
                } else {
                    polygonDoc = dbf.newDocumentBuilder().parse(file);
                }

                NodeList coords = polygonDoc.getElementsByTagName("coordinates");
                if (coords.getLength() == 0) {
                    JOptionPane.showMessageDialog(this, "Nenhuma tag <coordinates> encontrada.",
                            "Aviso", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String raw = coords.item(0).getTextContent().trim();
                StringBuilder sb = new StringBuilder();
                for (String tuple : raw.split("\\s+")) {
                    String[] parts = tuple.split(",");
                    if (parts.length >= 2)
                        sb.append(parts[0]).append(",").append(parts[1]).append("\n");
                }
                txtPolygon.setText(sb.toString().trim());
                statusLabel.setText("✅ Polígono carregado do KML.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao carregar KML de polígono:\n" + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        // ---- seleção de arquivos ----
        private void selecionarEntrada() {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Arquivo de entrada KML/KMZ");
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                txtInput.setText(ch.getSelectedFile().getAbsolutePath());
        }

        private void selecionarSaida() {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Arquivo de saída KML/KMZ");
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
                txtOutput.setText(ch.getSelectedFile().getAbsolutePath());
        }

        // ALGORITMO RAMER-DOUGLAS-PEUCKER (SIMPLIFICAÇÃO DE POLÍGONO)

        private String simplificarTextoPoligono(String rawTexto, double epsilon) {
            List<double[]> pts = new ArrayList<>();
            for (String linha : rawTexto.split("\\n")) {
                if(linha.isBlank()) continue;
                String[] p = linha.split(",");
                pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])});
            }
            
            List<double[]> simplificados = aplicarRDP(pts, epsilon);
            
            StringBuilder sb = new StringBuilder();
            for(double[] p : simplificados) {
                sb.append(p[0]).append(",").append(p[1]).append("\n");
            }
            return sb.toString();
        }

        private List<double[]> aplicarRDP(List<double[]> pts, double epsilon) {
            if (pts.size() < 3) return pts;
            
            int index = 0;
            double maxDist = 0;
            
            // Encontra o ponto mais distante do segmento de reta que liga o início ao fim
            for (int i = 1; i < pts.size() - 1; i++) {
                double dist = distanciaPerpendicular(pts.get(i), pts.get(0), pts.get(pts.size() - 1));
                if (dist > maxDist) {
                    index = i;
                    maxDist = dist;
                }
            }
            
            List<double[]> result = new ArrayList<>();
            // Se a distância máxima for maior que o epsilon, simplifica recursivamente
            if (maxDist > epsilon) {
                List<double[]> left = aplicarRDP(pts.subList(0, index + 1), epsilon);
                List<double[]> right = aplicarRDP(pts.subList(index, pts.size()), epsilon);
                
                result.addAll(left.subList(0, left.size() - 1));
                result.addAll(right);
            } else {
                result.add(pts.get(0));
                result.add(pts.get(pts.size() - 1));
            }
            return result;
        }

        private double distanciaPerpendicular(double[] pt, double[] lineStart, double[] lineEnd) {
            double x0 = pt[0], y0 = pt[1];
            double x1 = lineStart[0], y1 = lineStart[1];
            double x2 = lineEnd[0], y2 = lineEnd[1];
            
            double dx = x2 - x1;
            double dy = y2 - y1;
            
            if (dx == 0.0 && dy == 0.0) {
                return Math.sqrt(Math.pow(x0 - x1, 2) + Math.pow(y0 - y1, 2));
            }
            
            double num = Math.abs(dy * x0 - dx * y0 + x2 * y1 - y2 * x1);
            double den = Math.sqrt(dx * dx + dy * dy);
            return num / den;
        }

        // ---- ponto no polígono (ray-casting) ----
        private boolean estaDentro(double lon, double lat, double[][] poly) {
            int n = poly.length;
            boolean inside = false;
            double px = poly[0][0], py = poly[0][1];
            for (int i = 0; i < n + 1; i++) {
                double qx = poly[i % n][0], qy = poly[i % n][1];
                if (lat > Math.min(py, qy) && lat <= Math.max(py, qy)
                        && lon <= Math.max(px, qx) && py != qy) {
                    double xInt = (lat - py) * (qx - px) / (qy - py) + px;
                    if (px == qx || lon <= xInt) inside = !inside;
                }
                px = qx; py = qy;
            }
            return inside;
        }

        /**
         * Calcula o ponto de interseção entre o segmento (x1,y1)-(x2,y2)
         * e a aresta do polígono (x3,y3)-(x4,y4).
         * Retorna null se não houver interseção no intervalo dos segmentos.
         */
        private double[] intersecao(double x1, double y1, double x2, double y2,
                                     double x3, double y3, double x4, double y4) {
            double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
            if (Math.abs(denom) < 1e-12) return null; // paralelos
            double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
            double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                return new double[]{ x1 + t * (x2 - x1), y1 + t * (y2 - y1) };
            }
            return null;
        }

        /**
         * Recorta uma LineString pelo polígono usando o algoritmo de Cyrus-Beck adaptado:
         * percorre cada segmento da linha, detecta cruzamentos com as arestas do polígono
         * e divide em sub-segmentos internos.
         * Retorna uma lista de sub-linhas, cada uma como lista de pontos [lon, lat].
         */
        private List<List<double[]>> recortarLinha(List<double[]> pts, double[][] poly) {
            List<List<double[]>> resultado = new ArrayList<>();
            if (pts.isEmpty()) return resultado;

            List<double[]> segmentoAtual = new ArrayList<>();
            boolean dentroPrev = estaDentro(pts.get(0)[0], pts.get(0)[1], poly);
            if (dentroPrev) segmentoAtual.add(pts.get(0));

            for (int i = 1; i < pts.size(); i++) {
                double x1 = pts.get(i-1)[0], y1 = pts.get(i-1)[1];
                double x2 = pts.get(i)[0],   y2 = pts.get(i)[1];
                boolean dentroAtual = estaDentro(x2, y2, poly);

                // Coleta todos os pontos de cruzamento deste segmento com todas as arestas
                List<double[]> crossings = new ArrayList<>();
                int n = poly.length;
                for (int j = 0; j < n; j++) {
                    double x3 = poly[j][0],          y3 = poly[j][1];
                    double x4 = poly[(j+1) % n][0],  y4 = poly[(j+1) % n][1];
                    double[] pt = intersecao(x1, y1, x2, y2, x3, y3, x4, y4);
                    if (pt != null) crossings.add(pt);
                }

                // Ordena os cruzamentos pela distância a partir do ponto inicial do segmento
                crossings.sort((a, b) -> {
                    double da = (a[0]-x1)*(a[0]-x1) + (a[1]-y1)*(a[1]-y1);
                    double db = (b[0]-x1)*(b[0]-x1) + (b[1]-y1)*(b[1]-y1);
                    return Double.compare(da, db);
                });

                // Processa os cruzamentos alternando dentro/fora
                boolean estado = dentroPrev;
                for (double[] cross : crossings) {
                    if (estado) {
                        // Estava dentro, saindo: fecha o segmento atual no ponto de cruzamento
                        segmentoAtual.add(cross);
                        if (segmentoAtual.size() >= 2) resultado.add(new ArrayList<>(segmentoAtual));
                        segmentoAtual.clear();
                    } else {
                        // Estava fora, entrando: inicia novo segmento no cruzamento
                        segmentoAtual.add(cross);
                    }
                    estado = !estado;
                }

                // Adiciona o ponto final se estiver dentro
                if (dentroAtual) {
                    segmentoAtual.add(new double[]{x2, y2});
                }
                dentroPrev = dentroAtual;
            }

            // Fecha último segmento aberto
            if (segmentoAtual.size() >= 2) resultado.add(segmentoAtual);
            return resultado;
        }

        /**
         * Lê as coordenadas KML de uma tag <coordinates> e retorna lista de pontos [lon, lat, alt?].
         */
        private List<double[]> lerCoordenadas(String raw) {
            List<double[]> pts = new ArrayList<>();
            for (String tuple : raw.trim().split("\\s+")) {
                String[] p = tuple.split(",");
                if (p.length >= 2) {
                    double lon = Double.parseDouble(p[0].trim());
                    double lat = Double.parseDouble(p[1].trim());
                    double alt = (p.length >= 3) ? Double.parseDouble(p[2].trim()) : 0.0;
                    pts.add(new double[]{lon, lat, alt});
                }
            }
            return pts;
        }

        /**
         * Converte lista de pontos para string de coordenadas KML "lon,lat,alt".
         */
        private String pontosParaKml(List<double[]> pts) {
            StringBuilder sb = new StringBuilder();
            for (double[] p : pts) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(String.format(java.util.Locale.US, "%.8f,%.8f,%.1f", p[0], p[1], p.length > 2 ? p[2] : 0.0));
            }
            return sb.toString();
        }

        // ---- converte textarea → double[][] ----
        private double[][] converterTexto() throws IllegalArgumentException {
            String text = txtPolygon.getText().trim();
            if (text.isEmpty()) throw new IllegalArgumentException("Área de polígono vazia.");
            Pattern pat = Pattern.compile("^(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
            List<double[]> list = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Matcher m = pat.matcher(line);
                if (!m.matches())
                    throw new IllegalArgumentException("Formato inválido (esperado lon,lat): " + line);
                list.add(new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))});
            }
            if (list.size() < 3) throw new IllegalArgumentException("Polígono precisa de pelo menos 3 pontos.");
            return list.toArray(new double[0][]);
        }

        // ---- processar e filtrar ----
        private void processarEFiltrar() {
            String inputPath  = txtInput.getText();
            String outputPath = txtOutput.getText();
            if (inputPath.isEmpty() || outputPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Selecione os arquivos de entrada e saída.",
                        "Atenção", JOptionPane.ERROR_MESSAGE);
                return;
            }
            double[][] poly;
            try {
                poly = converterTexto();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Erro no polígono: " + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                if (inputPath.toLowerCase().endsWith(".kmz")) {
                    try (ZipFile zf = new ZipFile(new File(inputPath))) {
                        ZipEntry entry = zf.getEntry("doc.kml");
                        if (entry == null) entry = zf.getEntry("document.kml");
                        if (entry == null) throw new IOException("KMZ sem 'doc.kml'.");
                        try (InputStream is = zf.getInputStream(entry)) {
                            doc = dbf.newDocumentBuilder().parse(is);
                        }
                    }
                } else {
                    doc = dbf.newDocumentBuilder().parse(new File(inputPath));
                }

                List<Node> toRemove  = new ArrayList<>();
                // Placemarks de linha que precisam ser substituídos por múltiplos recortados
                List<Node[]> toSplit = new ArrayList<>(); // [placemark, novoCoordNode, sublinhas...]

                NodeList pms = doc.getElementsByTagName("Placemark");
                int total = pms.getLength();
                int totalPontos = 0, totalLinhas = 0;
                int pontosRemovidos = 0, linhasRecortadas = 0;

                for (int i = 0; i < total; i++) {
                    Node pm = pms.item(i);
                    Element pmEl = (Element) pm;

                    // ---- PONTO ----
                    Node pointNode = pmEl.getElementsByTagName("Point").item(0);
                    if (pointNode != null) {
                        totalPontos++;
                        Node coordNode = ((Element) pointNode).getElementsByTagName("coordinates").item(0);
                        if (coordNode == null) { toRemove.add(pm); pontosRemovidos++; continue; }
                        String[] parts = coordNode.getTextContent().trim().split(",");
                        double lon = Double.parseDouble(parts[0]);
                        double lat = Double.parseDouble(parts[1]);
                        if (!estaDentro(lon, lat, poly)) { toRemove.add(pm); pontosRemovidos++; }
                        continue;
                    }

                    // ---- LINESTRING ----
                    Node lineNode = pmEl.getElementsByTagName("LineString").item(0);
                    if (lineNode != null) {
                        totalLinhas++;
                        Node coordNode = ((Element) lineNode).getElementsByTagName("coordinates").item(0);
                        if (coordNode == null) { toRemove.add(pm); continue; }

                        List<double[]> pts = lerCoordenadas(coordNode.getTextContent());
                        List<List<double[]>> sublinhas = recortarLinha(pts, poly);

                        if (sublinhas.isEmpty()) {
                            // Linha inteiramente fora — remove
                            toRemove.add(pm);
                        } else if (sublinhas.size() == 1) {
                            // Linha parcialmente cortada — atualiza coordenadas no lugar
                            coordNode.setTextContent(pontosParaKml(sublinhas.get(0)));
                            linhasRecortadas++;
                        } else {
                            // Linha dividida em vários trechos — clona o Placemark para cada trecho
                            linhasRecortadas++;
                            // Atualiza o primeiro trecho no Placemark original
                            coordNode.setTextContent(pontosParaKml(sublinhas.get(0)));
                            // Cria novos Placemarks para os trechos adicionais
                            Node parent = pm.getParentNode();
                            for (int s = 1; s < sublinhas.size(); s++) {
                                Node clone = pm.cloneNode(true);
                                Node cloneCoord = ((Element) clone)
                                        .getElementsByTagName("coordinates").item(0);
                                cloneCoord.setTextContent(pontosParaKml(sublinhas.get(s)));
                                // Ajusta o nome para indicar o trecho
                                Node nameNode = ((Element) clone).getElementsByTagName("name").item(0);
                                if (nameNode != null)
                                    nameNode.setTextContent(nameNode.getTextContent() + " (" + (s+1) + ")");
                                parent.insertBefore(clone, pm.getNextSibling());
                            }
                        }
                        continue;
                    }

                    // ---- OUTROS (Polygon, MultiGeometry, etc.) ----
                }

                for (Node n : toRemove) n.getParentNode().removeChild(n);

                salvarDoc(doc, outputPath);

                int keptPontos = totalPontos - pontosRemovidos;
                String msg = "Filtro aplicado!\n"
                        + keptPontos + " de " + totalPontos + " pontos mantidos.\n"
                        + totalLinhas + " linhas processadas"
                        + (linhasRecortadas > 0 ? " (" + linhasRecortadas + " recortadas no limite)." : ".") + "\n"
                        + "Salvo em: " + outputPath;
                statusLabel.setText("\u2705 Pontos: " + keptPontos + "/" + totalPontos
                        + "  |  Linhas: " + totalLinhas
                        + (linhasRecortadas > 0 ? " (" + linhasRecortadas + " recortadas)" : ""));
                JOptionPane.showMessageDialog(this, msg, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao processar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

// PÁGINA 3 — EDITOR DE ESTILOS

    class StyleEditorPanel extends JPanel {

        private JTextField txtInput, txtOutput, txtKey;
        private JList<StyleListItem>      styleList;
        private DefaultListModel<StyleListItem> styleListModel;
        private JSlider    widthSlider;
        private JLabel     widthLabel;
        private ColorPreview colorPreview;
        private DefaultTableModel tableModel;
        private JTable     valuesTable;
        private Document   doc;
        private StyleListItem activeStyleItem;
        private HashMap<String, ArrayList<Node>> valueNodesMap = new HashMap<>();

        public StyleEditorPanel() {
            super(new BorderLayout(0, 4));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            // ---- arquivos ----
            JPanel filePanel = new JPanel();
            filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
            txtInput  = new JTextField();
            txtOutput = new JTextField();
            filePanel.add(fileRow("Arquivo de entrada (KML/KMZ):", txtInput,  e -> selecionarEntrada()));
            filePanel.add(fileRow("Arquivo de saída (KML/KMZ):",  txtOutput, e -> selecionarSaida()));
            add(filePanel, BorderLayout.NORTH);

            // ---- painel principal split ----
            JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            mainSplit.setResizeWeight(0.40);

            // ---- esquerda: lista de estilos ----
            JPanel stylePanel = new JPanel(new BorderLayout(0, 4));
            stylePanel.setBorder(BorderFactory.createTitledBorder("Estilos de Linha"));

            styleListModel = new DefaultListModel<>();
            styleList      = new JList<>(styleListModel);
            styleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            styleList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onStyleSelected(); });
            stylePanel.add(new JScrollPane(styleList), BorderLayout.CENTER);

            JPanel editControls = new JPanel();
            editControls.setLayout(new BoxLayout(editControls, BoxLayout.Y_AXIS));
            editControls.setBorder(new EmptyBorder(4, 4, 4, 4));

            // cor
            JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            colorPreview = new ColorPreview("ff000000");
            JButton btnMudarCor = new JButton("Mudar Cor...");
            btnMudarCor.addActionListener(e -> mudarCor());
            colorRow.add(btnMudarCor);
            colorRow.add(colorPreview);
            editControls.add(colorRow);

            // espessura
            JPanel widthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            widthSlider = new JSlider(1, 10, 1);
            widthSlider.setPaintTicks(true);
            widthSlider.setMajorTickSpacing(1);
            widthSlider.setSnapToTicks(true);
            widthSlider.addChangeListener(e -> mudarEspessura());
            widthLabel = new JLabel("Espessura: 1");
            widthRow.add(new JLabel("Espessura:"));
            widthRow.add(widthSlider);
            widthRow.add(widthLabel);
            editControls.add(widthRow);

            stylePanel.add(editControls, BorderLayout.SOUTH);
            mainSplit.setLeftComponent(stylePanel);

            // ---- direita: valores de balão ----
            JPanel balloonPanel = new JPanel(new BorderLayout(0, 4));
            balloonPanel.setBorder(BorderFactory.createTitledBorder("Editar Valores de Balão (únicos por chave)"));

            JPanel balloonTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            balloonTop.add(new JLabel("Chave (ex: Tensão):"));
            txtKey = new JTextField(20);
            balloonTop.add(txtKey);
            JButton btnLoad = new JButton("🔍  Carregar Valores");
            btnLoad.addActionListener(e -> carregarValoresBalao());
            balloonTop.add(btnLoad);
            balloonPanel.add(balloonTop, BorderLayout.NORTH);

            tableModel  = new DefaultTableModel(new String[]{"Valor único"}, 0);
            valuesTable = new JTable(tableModel);
            valuesTable.setAutoCreateRowSorter(true);
            balloonPanel.add(new JScrollPane(valuesTable), BorderLayout.CENTER);

            mainSplit.setRightComponent(balloonPanel);
            add(mainSplit, BorderLayout.CENTER);

            // ---- rodapé ----
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            JButton btnCarregar = new JButton("📂  Carregar KML");
            btnCarregar.addActionListener(e -> carregarKml());
            JButton btnSalvar = new JButton("💾  Salvar Alterações");
            btnSalvar.addActionListener(e -> salvarAlteracoes());
            footer.add(btnCarregar);
            footer.add(btnSalvar);
            add(footer, BorderLayout.SOUTH);
        }

        private void selecionarEntrada() {
            JFileChooser ch = new JFileChooser();
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                txtInput.setText(ch.getSelectedFile().getAbsolutePath());
        }

        private void selecionarSaida() {
            JFileChooser ch = new JFileChooser();
            ch.setFileFilter(new FileNameExtensionFilter("KML/KMZ", "kml", "kmz"));
            if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
                txtOutput.setText(ch.getSelectedFile().getAbsolutePath());
        }

        private void carregarKml() {
            String path = txtInput.getText();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Selecione um arquivo de entrada.", "Atenção", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                if (path.toLowerCase().endsWith(".kmz")) {
                    try (ZipFile zf = new ZipFile(new File(path))) {
                        ZipEntry entry = zf.getEntry("doc.kml");
                        if (entry == null) entry = zf.getEntry("document.kml");
                        try (InputStream is = zf.getInputStream(entry)) {
                            doc = dbf.newDocumentBuilder().parse(is);
                        }
                    }
                } else {
                    doc = dbf.newDocumentBuilder().parse(new File(path));
                }
                listarEstilos();
                if (!txtKey.getText().trim().isEmpty()) carregarValoresBalao();
                JOptionPane.showMessageDialog(this, "Arquivo carregado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void listarEstilos() {
            styleListModel.clear();
            if (doc == null) return;
            NodeList styles = doc.getElementsByTagName("Style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element styleEl = (Element) styles.item(i);
                String id = styleEl.getAttribute("id");
                NodeList ls = styleEl.getElementsByTagName("LineStyle");
                if (ls.getLength() == 0) continue;
                Element lineStyle = (Element) ls.item(0);
                Node cNode = lineStyle.getElementsByTagName("color").item(0);
                Node wNode = lineStyle.getElementsByTagName("width").item(0);
                String cor = (cNode != null) ? cNode.getTextContent() : "N/D";
                String esp = (wNode != null) ? wNode.getTextContent() : "N/D";
                styleListModel.addElement(new StyleListItem(id,
                        "ID: " + id + "  |  Cor: " + cor + "  |  Espessura: " + esp,
                        cor, esp, lineStyle));
            }
        }

        private void onStyleSelected() {
            int idx = styleList.getSelectedIndex();
            if (idx < 0) return;
            activeStyleItem = styleListModel.getElementAt(idx);
            colorPreview.set_color_from_kml(activeStyleItem.color);
            try {
                double w = Double.parseDouble(activeStyleItem.width);
                widthSlider.setValue((int) w);
                widthLabel.setText("Espessura: " + (int) w);
            } catch (NumberFormatException e) {
                widthSlider.setValue(1);
                widthLabel.setText("Espessura: N/D");
            }
        }

        private void mudarCor() {
            if (activeStyleItem == null) return;
            Color novo = JColorChooser.showDialog(this, "Selecionar cor", colorPreview.getColor());
            if (novo == null) return;
            String kmlColor = String.format("%02X%02X%02X%02X",
                    novo.getAlpha(), novo.getBlue(), novo.getGreen(), novo.getRed());
            Node cNode = ((Element) activeStyleItem.node).getElementsByTagName("color").item(0);
            if (cNode != null) {
                cNode.setTextContent(kmlColor);
                activeStyleItem.color = kmlColor;
                colorPreview.set_color(novo);
                styleList.repaint();
            }
        }

        private void mudarEspessura() {
            if (activeStyleItem == null) return;
            int w = widthSlider.getValue();
            widthLabel.setText("Espessura: " + w);
            Node wNode = ((Element) activeStyleItem.node).getElementsByTagName("width").item(0);
            if (wNode != null) {
                wNode.setTextContent(String.valueOf((double) w));
                activeStyleItem.width = String.valueOf((double) w);
                styleList.repaint();
            }
        }

        private void carregarValoresBalao() {
            if (doc == null) {
                JOptionPane.showMessageDialog(this, "Nenhum KML carregado.", "Atenção", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String key = txtKey.getText().trim();
            if (key.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Digite uma chave para buscar.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            tableModel.setRowCount(0);
            valueNodesMap.clear();
            List<String> seen = new ArrayList<>();
            NodeList dataNodes = doc.getElementsByTagName("Data");
            for (int i = 0; i < dataNodes.getLength(); i++) {
                Element dataEl = (Element) dataNodes.item(i);
                Node dnNode = dataEl.getElementsByTagName("displayName").item(0);
                if (dnNode == null || !dnNode.getTextContent().trim().equals(key)) continue;
                Node valNode = dataEl.getElementsByTagName("value").item(0);
                if (valNode == null) continue;
                String val;
                if (valNode.getFirstChild() != null && valNode.getFirstChild().getNodeType() == Node.CDATA_SECTION_NODE)
                    val = valNode.getFirstChild().getNodeValue();
                else
                    val = valNode.getTextContent().trim();
                if (!seen.contains(val)) {
                    seen.add(val);
                    tableModel.addRow(new Object[]{val});
                    valueNodesMap.put(val, new ArrayList<>());
                }
                valueNodesMap.get(val).add(valNode);
            }
        }

        private void salvarAlteracoes() {
            String out = txtOutput.getText();
            if (doc == null || out.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Preencha todos os campos e carregue um KML.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                // Persiste edições da tabela
                String key = txtKey.getText().trim();
                if (!key.isEmpty()) {
                    List<String> originalKeys = new ArrayList<>(valueNodesMap.keySet());
                    for (int i = 0; i < Math.min(tableModel.getRowCount(), originalKeys.size()); i++) {
                        String oldVal = originalKeys.get(i);
                        String newVal = (String) tableModel.getValueAt(i, 0);
                        ArrayList<Node> nodes = valueNodesMap.get(oldVal);
                        if (nodes != null) {
                            for (Node n : nodes) {
                                n.setTextContent("");
                                n.appendChild(doc.createCDATASection(newVal));
                            }
                        }
                    }
                }
                salvarDoc(doc, out);
                JOptionPane.showMessageDialog(this, "Arquivo salvo com sucesso!", "Concluído", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

// UTILITÁRIOS COMPARTILHADOS

    /**
     * Salva o Document XML em um arquivo KML ou KMZ.
     */
    static void salvarDoc(Document doc, String path) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        javax.xml.transform.Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        if (path.toLowerCase().endsWith(".kmz")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path))) {
                zos.putNextEntry(new ZipEntry("doc.kml"));
                zos.write(baos.toByteArray());
                zos.closeEntry();
            }
        } else {
            t.transform(new DOMSource(doc), new StreamResult(new File(path)));
        }
    }
}
