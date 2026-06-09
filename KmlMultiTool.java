// IMPORTS

import com.formdev.flatlaf.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.zip.*;

// --- CLASSE PRINCIPAL ---

public class KmlMultiTool extends JFrame {
    private CardLayout cardLayout;
    private JPanel container;
    private JButton btnPage1, btnPage2, btnPage3;
    /** Último diretório visitado — compartilhado entre todas as páginas */
    static File lastDir = null;

    public KmlMultiTool(boolean flatLafDisponivel) {
        setTitle("KML Multi-Ferramenta");
        configurarIcone();
        setSize(1125, 800);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        container = new JPanel(cardLayout);

        container.add(new ImageEditorPanel(), "page1");
        container.add(new StateFilterPanel(), "page2");
        container.add(new StyleEditorPanel(), "page3");

        // ---- Barra de Navegação com Toggle de Tema ----
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        navPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        btnPage1 = new JButton("🖊  Editor de Balão");
        btnPage2 = new JButton("📍  Filtro por Estado");
        btnPage3 = new JButton("🎨  Editor de Estilos");
        
        if (flatLafDisponivel) {
                    String[] temas = { "Flat Light", "Flat Dark", "Flat IntelliJ", "Flat Darcula" };
                    JComboBox<String> comboTema = new JComboBox<>(temas);
                    
                    // Define o tema inicial
                    comboTema.setSelectedItem("Flat Dark");
                    
                    comboTema.addActionListener(e -> {
                        String escolha = (String) comboTema.getSelectedItem();
                        switch (escolha) {
                            case "Flat Light": FlatLightLaf.setup(); break;
                            case "Flat Dark": FlatDarkLaf.setup(); break;
                            case "Flat IntelliJ": com.formdev.flatlaf.FlatIntelliJLaf.setup(); break;
                            case "Flat Darcula": com.formdev.flatlaf.FlatDarculaLaf.setup(); break;
                        }
                        SwingUtilities.updateComponentTreeUI(this);
                    });
                    
                    comboTema.setMaximumSize(new Dimension(120, 30));
                    navPanel.add(comboTema);
        } else {
            System.out.println("Modo de tema desativado por falta da biblioteca.");
        }

        btnPage1.addActionListener(e -> mudarPagina("page1", btnPage1));
        btnPage2.addActionListener(e -> mudarPagina("page2", btnPage2));
        btnPage3.addActionListener(e -> mudarPagina("page3", btnPage3));

        navPanel.add(btnPage1);
        navPanel.add(btnPage2);
        navPanel.add(btnPage3);

        add(navPanel, BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);
        mudarPagina("page1", btnPage1);
    }

    private void mudarPagina(String nomePagina, JButton ativo) {
        cardLayout.show(container, nomePagina);
        for (JButton b : new JButton[]{btnPage1, btnPage2, btnPage3}) 
            b.setFont(b.getFont().deriveFont(b == ativo ? Font.BOLD : Font.PLAIN));
    }

    private static JPanel fileRow(String label, JTextField field, ActionListener browse) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(new EmptyBorder(2, 4, 2, 4));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(220, 24));
        JButton btn = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        btn.setToolTipText("Selecionar arquivo");
        btn.addActionListener(browse);
        // Duplo-clique no campo também abre o seletor
        field.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) btn.doClick();
            }
        });
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.add(btn, BorderLayout.EAST);
        return row;
    }

    /** Cria JFileChooser já no último diretório usado. */
    static JFileChooser newChooser(String title, String... exts) {
        JFileChooser ch = new JFileChooser(lastDir);
        ch.setDialogTitle(title);
        if (exts.length > 0)
            ch.setFileFilter(new FileNameExtensionFilter(
                    String.join("/", exts).toUpperCase(), exts));
        return ch;
    }

    /** Diálogo de abertura — atualiza lastDir e devolve o arquivo ou null. */
    static File escolherAbrir(Component parent, String title, String... exts) {
        JFileChooser ch = newChooser(title, exts);
        if (ch.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            lastDir = ch.getSelectedFile().getParentFile();
            return ch.getSelectedFile();
        }
        return null;
    }

    /** Diálogo de salvamento — atualiza lastDir e devolve o arquivo ou null. */
    static File escolherSalvar(Component parent, String title, String... exts) {
        JFileChooser ch = newChooser(title, exts);
        if (ch.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            lastDir = ch.getSelectedFile().getParentFile();
            return ch.getSelectedFile();
        }
        return null;
    }

    private void configurarIcone() {
        try {
            java.net.URL imgURL = getClass().getResource("/KML.png");
            if (imgURL != null) setIconImage(new javax.swing.ImageIcon(imgURL).getImage());
        } catch (Exception e) { System.err.println("Ícone não encontrado."); }
    }

    public static void main(String[] args) {
        boolean flatLafDisponivel = false;
        try {
            Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            FlatDarkLaf.setup();
            flatLafDisponivel = true;
        } catch (ClassNotFoundException e) {
            System.err.println("Aviso: FlatLaf não encontrado. Usando tema padrão do sistema.");
        }

        final boolean finalFlatLafDisponivel = flatLafDisponivel;

        SwingUtilities.invokeLater(() -> {
            KmlMultiTool app = new KmlMultiTool(finalFlatLafDisponivel);
            app.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            app.setVisible(true);
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
        private int        placemarkIndex = 0;
        private JLabel     lblNavInfo;
        private JButton    btnNavPrev, btnNavNext;

        public ImageEditorPanel() {
            super(new BorderLayout(5, 5));
            setBorder(new EmptyBorder(10, 10, 10, 10));
        
            // ---- painel superior ----
            JPanel panelTop = new JPanel();
            panelTop.setLayout(new BoxLayout(panelTop, BoxLayout.Y_AXIS));
        
            txtInput  = new JTextField();
            txtOutput = new JTextField();
            panelTop.add(fileRow("Arquivo de entrada (KML/KMZ):", txtInput, e -> selecionarEntrada()));
            panelTop.add(fileRow("Arquivo de saída (KML/KMZ):",  txtOutput, e -> selecionarSaida()));
        
            JPanel panelOption = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            checkFormat = new JCheckBox("Aplicar nova formatação com cores e bordas");
            panelOption.add(checkFormat);
            panelOption.add(new JLabel("Separador:"));
            txtSeparator = new JTextField("=", 3);
            panelOption.add(txtSeparator);
            panelTop.add(panelOption);
        
            JPanel panelColors = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            panelColors.add(new JLabel("Cores:"));
            txtBgColor1    = addColorPicker(panelColors, "Fundo 1", "#DDDDFF");
            txtBgColor2    = addColorPicker(panelColors, "Fundo 2", "#FFFFFF");
            txtBorderColor = addColorPicker(panelColors, "Borda",   "#000000");
            panelTop.add(panelColors);
        
            // ---- painel esquerdo ----
            JPanel panelEditLeft = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;
            gbc.gridx = 0;
        
            // Seção Imagens
            gbc.weighty = 0; 
            panelEditLeft.add(sectionHeader("Imagens e Hiperlinks:", e -> addImageLinkPair()), gbc);
            
            imageLinkContainer = new JPanel();
            imageLinkContainer.setLayout(new BoxLayout(imageLinkContainer, BoxLayout.Y_AXIS));
            addImageLinkPair();
            JScrollPane scrollImages = new JScrollPane(imageLinkContainer);
            gbc.weighty = 0.5; 
            panelEditLeft.add(scrollImages, gbc);
        
            // Seção Substituir
            gbc.weighty = 0;
            panelEditLeft.add(sectionHeader("Substituir Palavras na Descrição:", e -> addFindReplacePair()), gbc);
            
            findReplaceContainer = new JPanel();
            findReplaceContainer.setLayout(new BoxLayout(findReplaceContainer, BoxLayout.Y_AXIS));
            addFindReplacePair();
            JScrollPane scrollReplace = new JScrollPane(findReplaceContainer);
            gbc.weighty = 1.0;
            panelEditLeft.add(scrollReplace, gbc);
        
            // ---- preview ----
            preview = new JEditorPane("text/html", "");
            preview.setEditable(false);
            JScrollPane scrollPreview = new JScrollPane(preview);
        
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelEditLeft, scrollPreview);
            split.setDividerLocation(575);
            split.setResizeWeight(0.45);
        
            // ---- botões ----
            JPanel panelBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            // Navegação de placemarks
            btnNavPrev = new JButton("◀");
            btnNavPrev.setToolTipText("Placemark anterior");
            btnNavPrev.setEnabled(false);
            btnNavPrev.addActionListener(e -> navegarPlacemark(-1));
            lblNavInfo = new JLabel("0 / 0");
            lblNavInfo.setPreferredSize(new Dimension(80, 24));
            lblNavInfo.setHorizontalAlignment(SwingConstants.CENTER);
            btnNavNext = new JButton("▶");
            btnNavNext.setToolTipText("Próximo placemark");
            btnNavNext.setEnabled(false);
            btnNavNext.addActionListener(e -> navegarPlacemark(+1));
            JButton btnExec = new JButton("💾  Executar e Salvar");
            btnExec.addActionListener(e -> executar());
            panelBtns.add(btnNavPrev);
            panelBtns.add(lblNavInfo);
            panelBtns.add(btnNavNext);
            panelBtns.add(Box.createHorizontalStrut(20));
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
            File f = KmlMultiTool.escolherAbrir(this, "Arquivo KML/KMZ de entrada", "kml", "kmz");
            if (f != null) {
                txtInput.setText(f.getAbsolutePath());
                KmlMultiTool.this.setTitle("KML Multi-Ferramenta — " + f.getName());
                carregarPreview(f.getAbsolutePath());
            }
        }

        private void selecionarSaida() {
            File f = KmlMultiTool.escolherSalvar(this, "Arquivo KML/KMZ de saída", "kml", "kmz");
            if (f != null) txtOutput.setText(f.getAbsolutePath());
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
                if (!placemarks.isEmpty()) {
                    placemarkIndex = 0;
                    atualizarNavegacao();
                    atualizarPreview();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao carregar arquivo:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void navegarPlacemark(int delta) {
            if (placemarks.isEmpty()) return;
            placemarkIndex = Math.max(0, Math.min(placemarks.size() - 1, placemarkIndex + delta));
            atualizarNavegacao();
            atualizarPreview();
        }

        private void atualizarNavegacao() {
            int total = placemarks.size();
            lblNavInfo.setText((placemarkIndex + 1) + " / " + total);
            btnNavPrev.setEnabled(placemarkIndex > 0);
            btnNavNext.setEnabled(placemarkIndex < total - 1);
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
            Node pm = placemarks.get(placemarkIndex);
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
            // Preview ao vivo ao digitar
            javax.swing.event.DocumentListener liveDL = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { atualizarPreview(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { atualizarPreview(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) {}
            };
            imgF.getDocument().addDocumentListener(liveDL);
            linkF.getDocument().addDocumentListener(liveDL);
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
            // Preview ao vivo ao digitar
            javax.swing.event.DocumentListener liveDL = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { atualizarPreview(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { atualizarPreview(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) {}
            };
            findF.getDocument().addDocumentListener(liveDL);
            replF.getDocument().addDocumentListener(liveDL);
            row.add(new JLabel("Buscar chave/valor:")); row.add(findF);
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
        private JButton      btnBuscar, btnProcessar;
        private JLabel       statusLabel;
        private JProgressBar progressBar;
        private JSpinner     spinBuffer;
        private Document     doc;

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
            JPanel footer = new JPanel();
            footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

            // linha 1: buffer + botão
            JPanel footerTop = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            footerTop.add(new JLabel("Buffer de margem (km):"));
            spinBuffer = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 500.0, 0.5));
            ((JSpinner.DefaultEditor) spinBuffer.getEditor()).getTextField().setColumns(5);
            spinBuffer.setToolTipText("<html>Expande o polígono em X km para incluir<br>"
                    + "pontos próximos à borda do estado.</html>");
            footerTop.add(spinBuffer);
            footerTop.add(Box.createHorizontalStrut(20));
            btnProcessar = new JButton("▶  Filtrar e Salvar");
            btnProcessar.addActionListener(e -> processarEFiltrar());
            footerTop.add(btnProcessar);
            footer.add(footerTop);

            // linha 2: barra de progresso + status
            JPanel footerBot = new JPanel(new BorderLayout(6, 0));
            footerBot.setBorder(new EmptyBorder(0, 8, 4, 8));
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setString("");
            progressBar.setPreferredSize(new Dimension(200, 18));
            statusLabel = new JLabel(" ");
            footerBot.add(progressBar, BorderLayout.WEST);
            footerBot.add(statusLabel, BorderLayout.CENTER);
            footer.add(footerBot);

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
                            // Aplicar simplificação de RDP para performance!
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
            File file = KmlMultiTool.escolherAbrir(this, "KML/KMZ com polígono", "kml", "kmz");
            if (file == null) return;

            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                Document polygonDoc;
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
            File f = KmlMultiTool.escolherAbrir(this, "Arquivo de entrada KML/KMZ", "kml", "kmz");
            if (f != null) txtInput.setText(f.getAbsolutePath());
        }

        private void selecionarSaida() {
            File f = KmlMultiTool.escolherSalvar(this, "Arquivo de saída KML/KMZ", "kml", "kmz");
            if (f != null) txtOutput.setText(f.getAbsolutePath());
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
        /** Calcula bounding box [minLon, minLat, maxLon, maxLat] do polígono. */
        private double[] calcBbox(double[][] poly) {
            double minLon = poly[0][0], minLat = poly[0][1];
            double maxLon = poly[0][0], maxLat = poly[0][1];
            for (double[] p : poly) {
                if (p[0] < minLon) minLon = p[0];
                if (p[0] > maxLon) maxLon = p[0];
                if (p[1] < minLat) minLat = p[1];
                if (p[1] > maxLat) maxLat = p[1];
            }
            return new double[]{minLon, minLat, maxLon, maxLat};
        }

        /**
         * Ray-casting com rejeição rápida por bounding box.
         * bbox = [minLon, minLat, maxLon, maxLat]
         */
        private boolean estaDentro(double lon, double lat, double[][] poly, double[] bbox) {
            // Rejeição imediata — cobre ~90% dos casos fora do estado
            if (lon < bbox[0] || lon > bbox[2] || lat < bbox[1] || lat > bbox[3]) return false;
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
        private List<List<double[]>> recortarLinha(List<double[]> pts, double[][] poly, double[] bbox) {
            List<List<double[]>> resultado = new ArrayList<>();
            if (pts.isEmpty()) return resultado;

            List<double[]> segmentoAtual = new ArrayList<>();
            boolean dentroPrev = estaDentro(pts.get(0)[0], pts.get(0)[1], poly, bbox);
            if (dentroPrev) segmentoAtual.add(pts.get(0));

            for (int i = 1; i < pts.size(); i++) {
                double x1 = pts.get(i-1)[0], y1 = pts.get(i-1)[1];
                double x2 = pts.get(i)[0],   y2 = pts.get(i)[1];
                boolean dentroAtual = estaDentro(x2, y2, poly, bbox);

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
            // Aplica buffer de margem (converte km → graus, ~111 km por grau)
            double bufferKm = ((Number) spinBuffer.getValue()).doubleValue();
            final double[][] polyFinal = (bufferKm > 0) ? aplicarBuffer(poly, bufferKm / 111.0) : poly;

            btnProcessar.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setString("Carregando...");
            statusLabel.setText("");

            new SwingWorker<String, Integer>() {
                @Override
                protected String doInBackground() throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setValidating(false);
                    dbf.setExpandEntityReferences(false);
                    try {
                        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    } catch (Exception _f) {}
                    Document docLocal;
                    if (inputPath.toLowerCase().endsWith(".kmz")) {
                        try (ZipFile zf = new ZipFile(new File(inputPath))) {
                            ZipEntry entry = zf.getEntry("doc.kml");
                            if (entry == null) entry = zf.getEntry("document.kml");
                            if (entry == null) throw new IOException("KMZ sem doc.kml.");
                            try (InputStream is = zf.getInputStream(entry)) {
                                docLocal = dbf.newDocumentBuilder().parse(is);
                            }
                        }
                    } else {
                        docLocal = dbf.newDocumentBuilder().parse(new File(inputPath));
                    }
                    publish(-1); // sinal: arquivo carregado, iniciando filtro

                    double[] bbox = calcBbox(polyFinal);
                    List<Node> toRemove = new ArrayList<>();
                    NodeList pmsLive = docLocal.getElementsByTagName("Placemark");
                    List<Node> allPms = new ArrayList<>();
                    for (int i = 0; i < pmsLive.getLength(); i++) allPms.add(pmsLive.item(i));
                    int total = allPms.size();
                    int totalPontos = 0, totalLinhas = 0, pontosRemovidos = 0, linhasRecortadas = 0;
                    for (int idx = 0; idx < total; idx++) {
                        Node pm = allPms.get(idx);
                        Element pmEl = (Element) pm;
                        // Publica progresso a cada 1%
                        int pct = (int) ((idx + 1) * 100.0 / total);
                        if (idx == 0 || pct > (int)(idx * 100.0 / total)) publish(pct);

                        Node pointNode = pmEl.getElementsByTagName("Point").item(0);
                        if (pointNode != null) {
                            totalPontos++;
                            Node coordNode = ((Element) pointNode).getElementsByTagName("coordinates").item(0);
                            if (coordNode == null) { toRemove.add(pm); pontosRemovidos++; continue; }
                            String[] parts = coordNode.getTextContent().trim().split(",");
                            double lon = Double.parseDouble(parts[0]);
                            double lat = Double.parseDouble(parts[1]);
                            if (!estaDentro(lon, lat, polyFinal, bbox)) { toRemove.add(pm); pontosRemovidos++; }
                            continue;
                        }
                        Node lineNode = pmEl.getElementsByTagName("LineString").item(0);
                        if (lineNode != null) {
                            totalLinhas++;
                            Node coordNode = ((Element) lineNode).getElementsByTagName("coordinates").item(0);
                            if (coordNode == null) { toRemove.add(pm); continue; }
                            List<double[]> pts = lerCoordenadas(coordNode.getTextContent());
                            List<List<double[]>> sublinhas = recortarLinha(pts, polyFinal, bbox);
                            if (sublinhas.isEmpty()) {
                                toRemove.add(pm);
                            } else if (sublinhas.size() == 1) {
                                coordNode.setTextContent(pontosParaKml(sublinhas.get(0)));
                                linhasRecortadas++;
                            } else {
                                linhasRecortadas++;
                                coordNode.setTextContent(pontosParaKml(sublinhas.get(0)));
                                Node parent = pm.getParentNode();
                                for (int s = 1; s < sublinhas.size(); s++) {
                                    Node clone = pm.cloneNode(true);
                                    Node cloneCoord = ((Element) clone).getElementsByTagName("coordinates").item(0);
                                    cloneCoord.setTextContent(pontosParaKml(sublinhas.get(s)));
                                    Node nameNode = ((Element) clone).getElementsByTagName("name").item(0);
                                    if (nameNode != null)
                                        nameNode.setTextContent(nameNode.getTextContent() + " (" + (s+1) + ")");
                                    parent.insertBefore(clone, pm.getNextSibling());
                                }
                            }
                            continue;
                        }
                    }
                    for (Node n : toRemove) n.getParentNode().removeChild(n);
                    salvarDoc(docLocal, outputPath);
                    int keptPontos = totalPontos - pontosRemovidos;
                    return "Filtro aplicado!\n"
                            + keptPontos + " de " + totalPontos + " pontos mantidos.\n"
                            + totalLinhas + " linhas processadas"
                            + (linhasRecortadas > 0 ? " (" + linhasRecortadas + " recortadas)." : ".") + "\n"
                            + (bufferKm > 0 ? "Buffer aplicado: " + bufferKm + " km.\n" : "")
                            + "Salvo em: " + outputPath
                            + "|||" + keptPontos + "/" + totalPontos
                            + "  |  Linhas: " + totalLinhas
                            + (linhasRecortadas > 0 ? " (" + linhasRecortadas + " recortadas)" : "");
                }
                @Override
                protected void process(List<Integer> chunks) {
                    int last = chunks.get(chunks.size() - 1);
                    if (last == -1) {
                        progressBar.setString("Filtrando...");
                    } else {
                        progressBar.setValue(last);
                        progressBar.setString(last + "%");
                    }
                }
                @Override
                protected void done() {
                    btnProcessar.setEnabled(true);
                    progressBar.setValue(100);
                    try {
                        String result = get();
                        String[] parts = result.split("\\|\\|\\|");
                        String msg    = parts[0];
                        String status = parts.length > 1 ? parts[1] : "";
                        progressBar.setString("Concluído");
                        statusLabel.setText("✅ " + status);
                        JOptionPane.showMessageDialog(StateFilterPanel.this, msg, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        progressBar.setString("Erro");
                        statusLabel.setText("❌ " + ex.getMessage());
                        JOptionPane.showMessageDialog(StateFilterPanel.this,
                                "Erro ao processar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        /**
         * Expande cada vértice do polígono radialmente por deltaGraus.
         * Equivale a um buffer circular uniforme em coordenadas geográficas.
         */
        private double[][] aplicarBuffer(double[][] poly, double deltaGraus) {
            int n = poly.length;
            // Calcula centróide
            double cx = 0, cy = 0;
            for (double[] p : poly) { cx += p[0]; cy += p[1]; }
            cx /= n; cy /= n;
            double[][] resultado = new double[n][2];
            for (int i = 0; i < n; i++) {
                double dx = poly[i][0] - cx, dy = poly[i][1] - cy;
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < 1e-10) { resultado[i] = poly[i].clone(); continue; }
                // Move o vértice para longe do centróide por deltaGraus
                resultado[i][0] = poly[i][0] + dx / dist * deltaGraus;
                resultado[i][1] = poly[i][1] + dy / dist * deltaGraus;
            }
            return resultado;
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
        private LinkedHashMap<String, ArrayList<Node>> valueNodesMap = new LinkedHashMap<>();
        private JLabel     statusLabel;
        private JButton    btnSalvar;
        private JButton    btnLoad;
        private JCheckBox  checkConsolidarExtras;

        public StyleEditorPanel() {
            super(new BorderLayout(0, 4));
            setBorder(new EmptyBorder(6, 8, 6, 8));
        
            // ---- arquivos ----
            JPanel filePanel = new JPanel();
            filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
            txtInput = new JTextField();
            txtOutput = new JTextField();
            filePanel.add(fileRow("Arquivo de entrada (KML/KMZ):", txtInput, e -> selecionarEntrada()));
            filePanel.add(fileRow("Arquivo de saída (KML/KMZ):", txtOutput, e -> selecionarSaida()));
            statusLabel = new JLabel(" Selecione um arquivo de entrada para começar.");
            statusLabel.setBorder(new EmptyBorder(2, 6, 2, 6));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
            filePanel.add(statusLabel);
            add(filePanel, BorderLayout.NORTH);
        
            // ---- painel principal split ----
            JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            mainSplit.setResizeWeight(0.30);
            mainSplit.setDividerLocation(575);
            mainSplit.setContinuousLayout(true);   // ← melhora visual durante resize
        
            // ==== ESQUERDA: Estilos ====
            JPanel stylePanel = new JPanel(new BorderLayout(0, 4));
            stylePanel.setBorder(BorderFactory.createTitledBorder("Estilos de Linha"));
        
            styleListModel = new DefaultListModel<>();
            styleList = new JList<>(styleListModel);
            styleList.setFont(new Font("Monospaced", Font.PLAIN, 12));
            styleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            styleList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onStyleSelected(); });
        
            stylePanel.add(new JScrollPane(styleList), BorderLayout.CENTER);
        
            // --- Controles de edição (3 linhas mais resistentes) ---
            JPanel editControls = new JPanel();
            editControls.setLayout(new BoxLayout(editControls, BoxLayout.Y_AXIS));
            editControls.setBorder(new EmptyBorder(6, 6, 6, 6));
        
            // Linha 1: Cor + Espessura
            JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            colorPreview = new ColorPreview("ff000000");
            JButton btnMudarCor = new JButton("Mudar Cor...");
            btnMudarCor.addActionListener(e -> mudarCor());
        
            row1.add(btnMudarCor);
            row1.add(colorPreview);
            row1.add(Box.createHorizontalStrut(12));
            row1.add(new JLabel("Espessura:"));
            widthSlider = new JSlider(1, 5, 1);
            widthSlider.setPaintLabels(true);
            widthSlider.setPaintTicks(true);
            widthSlider.setMajorTickSpacing(1);
            widthSlider.setSnapToTicks(true);
            widthSlider.setPreferredSize(new Dimension(150, 50));
            widthSlider.addChangeListener(e -> {
            if (activeStyleItem == null) {
                widthLabel.setText("Espessura:");
                return;
            }
        
            try {
                double atual = Double.parseDouble(activeStyleItem.width);
                int novo = widthSlider.getValue();
        
                if (novo == (int)Math.round(atual))
                    widthLabel.setText("Esp: " + atual);
                else
                    widthLabel.setText("Esp: " + atual + " → " + novo);
        
            } catch (Exception ex) {
                widthLabel.setText("Esp: " + widthSlider.getValue());
            }
        });
        
            widthLabel = new JLabel("Espessura:");
            widthLabel.setPreferredSize(new Dimension(75, 20));
        
            row1.add(widthSlider);
            JButton btnRenomear = new JButton("Renomear IDs");
            btnRenomear.setToolTipText(
                "<html>Substitui IDs numéricos por nomes legíveis<br>" +
                "baseados na cor e espessura.</html>"
            );
            btnRenomear.addActionListener(e -> renomearIdSelecionado());
            
            row1.add(Box.createHorizontalStrut(8));
            row1.add(btnRenomear);
            editControls.add(row1);
        
            // Linha 2: reserva
            JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            editControls.add(row2);
        
            // Linha 3: Organização
            JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            JButton btnConsolidar = new JButton("🔗 Consolidar Duplicados");
            btnConsolidar.setToolTipText("Agrupa Styles com mesmos valores e atualiza todos os styleUrl");
            btnConsolidar.addActionListener(e -> consolidarEstilos());
        
            JButton btnRemoverSemUso = new JButton("🗑 Remover Sem Uso");
            btnRemoverSemUso.setToolTipText("Remove Styles não referenciados por nenhum Placemark");
            btnRemoverSemUso.addActionListener(e -> removerStylesSemUso());
        
            checkConsolidarExtras = new JCheckBox("Incluir PolyStyle/IconStyle");
            checkConsolidarExtras.setToolTipText("<html>Marcado: considera PolyStyle e IconStyle na comparação.<br>Desmarcado: compara apenas Cor e Espessura da linha.</html>");
        
            row3.add(btnConsolidar);
            row3.add(btnRemoverSemUso);
            row3.add(Box.createHorizontalStrut(8));
            row3.add(checkConsolidarExtras);
            editControls.add(row3);
        
            stylePanel.add(editControls, BorderLayout.SOUTH);
            mainSplit.setLeftComponent(stylePanel);
        
            // DIREITA: Valores de Balão
            JPanel balloonPanel = new JPanel(new BorderLayout(0, 4));
            balloonPanel.setBorder(BorderFactory.createTitledBorder("Editar Valores de Balão (únicos por chave)"));
        
            // Topo mais resiliente ao redimensionamento
            JPanel balloonTop = new JPanel(new BorderLayout(6, 0));
            balloonTop.setBorder(new EmptyBorder(4, 4, 4, 4));
        
            JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            leftTop.add(new JLabel("Chave (ex: Tensão):"));
            txtKey = new JTextField(14);
            txtKey.addActionListener(e -> carregarValoresBalao());
            leftTop.add(txtKey);
        
            JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            btnLoad = new JButton("Carregar Valores");
            btnLoad.addActionListener(e -> carregarValoresBalao());
            rightTop.add(btnLoad);
        
            balloonTop.add(leftTop, BorderLayout.WEST);
            balloonTop.add(rightTop, BorderLayout.EAST);
        
            balloonPanel.add(balloonTop, BorderLayout.NORTH);
        
            tableModel = new DefaultTableModel(new String[]{"Valor único", "Ocorrências"}, 0) {
                @Override public boolean isCellEditable(int row, int col) { return col == 0; }
                @Override public Class<?> getColumnClass(int col) { return col == 1 ? Integer.class : String.class; }
            };
            valuesTable = new JTable(tableModel);
            valuesTable.setAutoCreateRowSorter(true);
            valuesTable.getColumnModel().getColumn(1).setPreferredWidth(90);
            valuesTable.getColumnModel().getColumn(1).setMaxWidth(110);
        
            balloonPanel.add(new JScrollPane(valuesTable), BorderLayout.CENTER);
        
            mainSplit.setRightComponent(balloonPanel);
            add(mainSplit, BorderLayout.CENTER);
        
            // ---- rodapé ----
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            btnSalvar = new JButton("💾 Salvar Alterações");
            btnSalvar.setEnabled(false);
            btnSalvar.addActionListener(e -> salvarAlteracoes());
            footer.add(btnSalvar);
            add(footer, BorderLayout.SOUTH);
        }

        private void selecionarEntrada() {
            File f = KmlMultiTool.escolherAbrir(this, "Arquivo de entrada KML/KMZ", "kml", "kmz");
            if (f != null) {
                txtInput.setText(f.getAbsolutePath());
                carregarKml(f.getAbsolutePath());
            }
        }

        private void selecionarSaida() {
            File f = KmlMultiTool.escolherSalvar(this, "Arquivo de saída KML/KMZ", "kml", "kmz");
            if (f != null) txtOutput.setText(f.getAbsolutePath());
        }

        private void carregarKml(String path) {
            if (path == null || path.isEmpty()) return;
            statusLabel.setText("⏳ Carregando " + new File(path).getName() + "...");
            btnSalvar.setEnabled(false);
            styleListModel.clear();
            tableModel.setRowCount(0);

            new SwingWorker<Document, Void>() {
                @Override
                protected Document doInBackground() throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    if (path.toLowerCase().endsWith(".kmz")) {
                        try (ZipFile zf = new ZipFile(new File(path))) {
                            ZipEntry entry = zf.getEntry("doc.kml");
                            if (entry == null) entry = zf.getEntry("document.kml");
                            if (entry == null) throw new IOException("KMZ sem 'doc.kml'.");
                            try (InputStream is = zf.getInputStream(entry)) {
                                return dbf.newDocumentBuilder().parse(is);
                            }
                        }
                    } else {
                        return dbf.newDocumentBuilder().parse(new File(path));
                    }
                }

                @Override
                protected void done() {
                    try {
                        doc = get();
                        listarEstilos();
                        //if (!txtKey.getText().trim().isEmpty()) carregarValoresBalao(); // busca automaticamente
                        int n = styleListModel.getSize();
                        statusLabel.setText("✅ " + new File(path).getName()
                                + " — " + n + " estilo(s) de linha encontrado(s).");
                        btnSalvar.setEnabled(true);
                    } catch (Exception ex) {
                        doc = null;
                        statusLabel.setText("❌ Erro: " + ex.getMessage());
                        JOptionPane.showMessageDialog(StyleEditorPanel.this,
                                "Erro ao carregar:\n" + ex.getMessage(),
                                "Erro", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private void listarEstilos() {
            styleListModel.clear();
            if (doc == null) return;
        
            // 1. Mapeia todos os Style URLs usados pelos Placemarks
            Map<String, Integer> styleUsageMap = new HashMap<>();
            NodeList styleUrls = doc.getElementsByTagName("styleUrl");
            for (int i = 0; i < styleUrls.getLength(); i++) {
                String url = styleUrls.item(i).getTextContent().replace("#", "");
                styleUsageMap.put(url, styleUsageMap.getOrDefault(url, 0) + 1);
            }
        
            // 2. Lista os estilos e associa a contagem
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
                int uso = styleUsageMap.getOrDefault(id, 0);
        
                styleListModel.addElement(new StyleListItem(id, "", cor, esp, lineStyle, uso));
            }
        }

        private void onStyleSelected() {
            List<StyleListItem> sel = styleList.getSelectedValuesList();
            if (sel.isEmpty()) {
                activeStyleItem = null;
                widthLabel.setText("Espessura:");
                return;
            }
            activeStyleItem = sel.get(0);
            if (sel.size() > 1) {
                widthLabel.setText(sel.size() + " selecionados — clique Aplicar a Seleção");
                return;
            }
            // seleção única: atualiza cor e slider
            if (activeStyleItem.color != null && !"N/D".equals(activeStyleItem.color))
                colorPreview.set_color_from_kml(activeStyleItem.color);
            try {
                double w = Double.parseDouble(activeStyleItem.width);
                for (javax.swing.event.ChangeListener cl : widthSlider.getChangeListeners())
                    widthSlider.removeChangeListener(cl);
                widthSlider.setValue(Math.min(10, Math.max(1, (int) Math.round(w))));
                widthLabel.setText("Espessura: " + w);
                widthSlider.addChangeListener(e -> mudarEspessura());
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
                activeStyleItem.recalcularOpacidade();
                colorPreview.set_color(novo);
                styleList.repaint();
            }
        }

        private void mudarEspessura() {
            if (!widthSlider.getValueIsAdjusting()) {
                if (activeStyleItem == null) return;
                int w = widthSlider.getValue();
                widthLabel.setText("Espessura: " + w);
                Node wNode = ((Element) activeStyleItem.node).getElementsByTagName("width").item(0);
                if (wNode != null) {
                    String novoValor = String.valueOf((double) w);
                    // Só atualiza o XML se o valor for diferente do atual
                    if (!wNode.getTextContent().equals(novoValor)) {
                        wNode.setTextContent(novoValor);
                        activeStyleItem.width = novoValor;
                        activeStyleItem.recalcularOpacidade();
                        styleList.repaint();
                    }
                }
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
    
        // 1. Prepara a UI para o estado "Pesquisando"
        btnLoad.setEnabled(false);
        btnLoad.setText("Pesquisando...");
        statusLabel.setText("⏳ Buscando valores para a chave \"" + key + "\"...");
        tableModel.setRowCount(0);
    
        // 2. SwingWorker para não travar a tela
        new SwingWorker<Map<String, ArrayList<Node>>, Void>() {
            
            @Override
            protected Map<String, ArrayList<Node>> doInBackground() throws Exception {
                // Este mapa será o resultado da busca feita em segundo plano
                Map<String, ArrayList<Node>> tempMap = new LinkedHashMap<>();
                NodeList dataNodes = doc.getElementsByTagName("Data");
                
                for (int i = 0; i < dataNodes.getLength(); i++) {
                    Element dataEl = (Element) dataNodes.item(i);
                    Node dnNode = dataEl.getElementsByTagName("displayName").item(0);
                    if (dnNode == null || !dnNode.getTextContent().trim().equals(key)) continue;
                    
                    Node valNode = dataEl.getElementsByTagName("value").item(0);
                    if (valNode == null) continue;
                    
                    String val = (valNode.getFirstChild() != null && valNode.getFirstChild().getNodeType() == Node.CDATA_SECTION_NODE)
                            ? valNode.getFirstChild().getNodeValue()
                            : valNode.getTextContent().trim();
                    
                    tempMap.computeIfAbsent(val, k -> new ArrayList<>()).add(valNode);
                }
                return tempMap;
            }
    
            @Override
            protected void done() {
                try {
                    // 3. Recebe o resultado e atualiza a UI na Thread Principal
                    valueNodesMap = (LinkedHashMap<String, ArrayList<Node>>) get(); 
                    
                    if (valueNodesMap.isEmpty()) {
                        statusLabel.setText("⚠ Nenhum valor encontrado para chave \"" + key + "\".");
                    } else {
                        for (Map.Entry<String, ArrayList<Node>> entry : valueNodesMap.entrySet()) {
                            tableModel.addRow(new Object[]{entry.getKey(), entry.getValue().size()});
                        }
                        statusLabel.setText("🔑 Chave \"" + key + "\": " + valueNodesMap.size() + " valor(es) único(s).");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Erro na busca.");
                    JOptionPane.showMessageDialog(StyleEditorPanel.this, "Erro: " + ex.getMessage());
                } finally {
                    // 4. Restaura o botão
                    btnLoad.setEnabled(true);
                    btnLoad.setText("Carregar Valores");
                }
            }
        }.execute();
    }

        private void renomearIdSelecionado() {
            if (activeStyleItem == null) {
                JOptionPane.showMessageDialog(this, "Selecione um estilo primeiro.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
        
            String idAtual = activeStyleItem.id;
            String novoId = JOptionPane.showInputDialog(this, "Novo ID:", idAtual);
        
            if (novoId == null) return;
            novoId = normalizarId(novoId.trim());
        
            if (novoId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "O ID não pode ficar vazio.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
        
            if (novoId.equals(idAtual)) return;
        
            NodeList styles = doc.getElementsByTagName("Style");
            for (int i = 0; i < styles.getLength(); i++) {
                if (novoId.equals(((Element) styles.item(i)).getAttribute("id"))) {
                    JOptionPane.showMessageDialog(this, "Já existe um Style com esse ID.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        
            ((Element) activeStyleItem.node.getParentNode()).setAttribute("id", novoId);
        
            NodeList styleUrls = doc.getElementsByTagName("styleUrl");
            for (int i = 0; i < styleUrls.getLength(); i++) {
                Node n = styleUrls.item(i);
                String ref = n.getTextContent().trim();
                String idRef = ref.startsWith("#") ? ref.substring(1) : ref;
                if (idRef.equals(idAtual)) n.setTextContent("#" + novoId);
            }
        
            activeStyleItem.id = novoId;
            activeStyleItem.recalcularOpacidade();
            styleList.repaint();
            statusLabel.setText("ID alterado: " + idAtual + " → " + novoId);
        }
        
        private String normalizarId(String s) {
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}|[^A-Za-z0-9_-]", "_");
            return s.replaceAll("_+", "_").trim();
        }

        private void consolidarEstilos() {
            if (doc == null) { JOptionPane.showMessageDialog(this, "Carregue um arquivo KML primeiro.", "Atenção", JOptionPane.WARNING_MESSAGE); return; }
            boolean usarExtras = checkConsolidarExtras.isSelected();
            LinkedHashMap<String, String> chaveParaRep = new LinkedHashMap<>();
            HashMap<String, String> remapIds = new HashMap<>();
            NodeList styles = doc.getElementsByTagName("Style");
            List<Node> allStyles = new ArrayList<>();
            for (int i = 0; i < styles.getLength(); i++) allStyles.add(styles.item(i));
            List<Node> stylesToRemove = new ArrayList<>();
            for (Node styleNode : allStyles) {
                Element styleEl = (Element) styleNode;
                String id = styleEl.getAttribute("id");
                if (id.isEmpty()) continue;
                String cor = "N/D", esp = "N/D";
                NodeList ls = styleEl.getElementsByTagName("LineStyle");
                if (ls.getLength() > 0) {
                    Element lineStyle = (Element) ls.item(0);
                    Node cNode = lineStyle.getElementsByTagName("color").item(0);
                    Node wNode = lineStyle.getElementsByTagName("width").item(0);
                    cor = (cNode != null) ? cNode.getTextContent().trim() : "N/D";
                    esp = (wNode != null) ? wNode.getTextContent().trim() : "N/D";
                }
                StringBuilder chave = new StringBuilder(cor).append("|").append(esp);
                if (usarExtras) {
                    NodeList ps = styleEl.getElementsByTagName("PolyStyle");
                    if (ps.getLength() > 0) { Node p = ((Element)ps.item(0)).getElementsByTagName("color").item(0); chave.append("|poly:").append(p!=null?p.getTextContent().trim():"N/D"); }
                    NodeList il = styleEl.getElementsByTagName("IconStyle");
                    if (il.getLength() > 0) { NodeList h = ((Element)il.item(0)).getElementsByTagName("href"); chave.append("|icon:").append(h.getLength()>0?h.item(0).getTextContent().trim():"N/D"); }
                }
                String chaveStr = chave.toString();
                if (!chaveParaRep.containsKey(chaveStr)) { chaveParaRep.put(chaveStr, id); remapIds.put(id, id); }
                else { remapIds.put(id, chaveParaRep.get(chaveStr)); stylesToRemove.add(styleNode); }
            }
            if (stylesToRemove.isEmpty()) { JOptionPane.showMessageDialog(this, "Nenhum Style duplicado encontrado.", "Sem duplicatas", JOptionPane.INFORMATION_MESSAGE); return; }
            NodeList styleUrls = doc.getElementsByTagName("styleUrl");
            int urlsAtualizados = 0;
            for (int i = 0; i < styleUrls.getLength(); i++) {
                Node urlNode = styleUrls.item(i);
                String ref = urlNode.getTextContent().trim();
                String idRef = ref.startsWith("#") ? ref.substring(1) : ref;
                String rep = remapIds.get(idRef);
                if (rep != null && !rep.equals(idRef)) { urlNode.setTextContent("#" + rep); urlsAtualizados++; }
            }
            for (Node n : stylesToRemove) n.getParentNode().removeChild(n);
            listarEstilos();
            statusLabel.setText("Consolidado: " + stylesToRemove.size() + " duplicado(s), " + urlsAtualizados + " ref. atualizada(s).");
            JOptionPane.showMessageDialog(this, stylesToRemove.size() + " Style(s) removido(s).\n" + urlsAtualizados + " <styleUrl> redirecionado(s).\n\nClique em Salvar para gravar.", "Consolidação", JOptionPane.INFORMATION_MESSAGE);
        }

        private void removerStylesSemUso() {
            if (doc == null) { JOptionPane.showMessageDialog(this, "Carregue um arquivo KML primeiro.", "Atenção", JOptionPane.WARNING_MESSAGE); return; }
            Set<String> idsRef = new HashSet<>();
            NodeList styleUrls = doc.getElementsByTagName("styleUrl");
            for (int i = 0; i < styleUrls.getLength(); i++) { String r = styleUrls.item(i).getTextContent().trim(); idsRef.add(r.startsWith("#")?r.substring(1):r); }
            NodeList styleMaps = doc.getElementsByTagName("StyleMap");
            for (int i = 0; i < styleMaps.getLength(); i++) {
                Element sm = (Element) styleMaps.item(i);
                String smId = sm.getAttribute("id"); if (!smId.isEmpty()) idsRef.add(smId);
                NodeList pairs = sm.getElementsByTagName("styleUrl");
                for (int j = 0; j < pairs.getLength(); j++) { String r = pairs.item(j).getTextContent().trim(); idsRef.add(r.startsWith("#")?r.substring(1):r); }
            }
            NodeList styles = doc.getElementsByTagName("Style");
            List<Node> allStyles = new ArrayList<>();
            for (int i = 0; i < styles.getLength(); i++) allStyles.add(styles.item(i));
            List<Node> toRemove = new ArrayList<>();
            for (Node n : allStyles) { String id = ((Element)n).getAttribute("id"); if (!id.isEmpty() && !idsRef.contains(id)) toRemove.add(n); }
            if (toRemove.isEmpty()) { JOptionPane.showMessageDialog(this, "Nenhum Style sem uso encontrado.", "Tudo em uso", JOptionPane.INFORMATION_MESSAGE); return; }
            for (Node n : toRemove) n.getParentNode().removeChild(n);
            listarEstilos();
            statusLabel.setText("Removido(s): " + toRemove.size() + " Style(s) sem uso.");
            JOptionPane.showMessageDialog(this, toRemove.size() + " Style(s) removido(s).\n\nClique em Salvar para gravar.", "Limpeza concluída", JOptionPane.INFORMATION_MESSAGE);
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
                statusLabel.setText("✅ Salvo em: " + new File(out).getName());
                JOptionPane.showMessageDialog(this, "Arquivo salvo com sucesso!", "Concluído", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- CLASSES AUXILIARES ---
    
    static class ColorPreview extends JPanel {
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
            } catch (Exception e) { this.color = Color.BLACK; }
            repaint();
        }
        public void set_color(Color color) { this.color = color; repaint(); }
        public Color getColor() { return this.color; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (color != null) { g.setColor(color); g.fillRect(0, 0, getWidth(), getHeight()); }
        }
    }
    
    static class StyleListItem {
        String id, label, color, width, opacity;
        int usageCount;
        Node node;

        public StyleListItem(String id, String label, String color, String width, Node node, int usageCount) {
            this.id = id;
            this.label = label;
            this.color = color;
            this.width = width;
            this.node = node;
            this.usageCount = usageCount;
            
            // Chama o cálculo logo ao construir
            recalcularOpacidade();
        }

        public void recalcularOpacidade() {
            if (color != null && color.length() >= 8) {
                try {
                    int alphaDecimal = Integer.parseInt(color.substring(0, 2), 16);
                    this.opacity = (int) ((alphaDecimal / 255.0) * 100) + "%";
                } catch (Exception e) { this.opacity = "N/D"; }
            } else {
                this.opacity = "N/D";
            }
            
            // Define o label que o JList vai exibir
            this.label = String.format("ID: %4s | Cor: %8s | Transp: %4s | Esp: %5s | Uso: %d", 
                                       id, color, opacity, width, usageCount);
        }

        @Override 
        public String toString() { 
            return this.label; 
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
