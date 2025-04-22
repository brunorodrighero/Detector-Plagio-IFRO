package com.detectordecopiasplagio;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class PlagiarismDetector extends JFrame {
    // GUI Components (marked as final since they are not reassigned)
    private final JTextField folderPathField;
    private final JTextField reportPathField;
    private final JTextField thresholdField;
    private final JTextArea resultArea;
    private final JButton selectFolderButton;
    private final JButton selectReportPathButton;
    private final JButton startButton;

    // Constants
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.04; // 4% as default
    private static final int NGRAM_SIZE = 5;
    private static final int MAX_EXCERPTS_TO_DISPLAY = 3;
    private static final String REPORT_FILE_NAME = "RelatorioPlagio.txt";

    // Class to store PDF information
    static class PDFInfo {
        String fileName;
        String filePath;
        String author;
        String title;
        String text;
        List<String> nGrams;

        PDFInfo(String fileName, String filePath, String author, String title, String text) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.author = author != null ? author : "Desconhecido";
            this.title = title != null ? title : "Sem título";
            this.text = text;
            this.nGrams = generateNGrams(text);
        }
    }

    // Class to store plagiarism results
    static class PlagiarismResult {
        String file1;
        String filePath1;
        String file2;
        String filePath2;
        List<String> copiedExcerpts;
        double similarity;
        boolean hasPlagiarism;

        PlagiarismResult(String file1, String filePath1, String file2, String filePath2, List<String> copiedExcerpts, double similarity, boolean hasPlagiarism) {
            this.file1 = file1;
            this.filePath1 = filePath1;
            this.file2 = file2;
            this.filePath2 = filePath2;
            this.copiedExcerpts = copiedExcerpts;
            this.similarity = similarity;
            this.hasPlagiarism = hasPlagiarism;
        }
    }

    public PlagiarismDetector() {
        // Configure the window
        setTitle("Detector de Plágio");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top panel for controls
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Folder selection field and button
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("Pasta para análise:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        folderPathField = new JTextField("", 20);
        controlPanel.add(folderPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        selectFolderButton = new JButton("Selecionar Pasta");
        selectFolderButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            int result = fileChooser.showOpenDialog(PlagiarismDetector.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                folderPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        controlPanel.add(selectFolderButton, gbc);

        // Report save path field and button
        gbc.gridx = 0;
        gbc.gridy = 1;
        controlPanel.add(new JLabel("Salvar relatório em:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        // Default to the user's Desktop directory
        String defaultReportPath = System.getProperty("user.home") + File.separator + "Desktop" + File.separator + REPORT_FILE_NAME;
        reportPathField = new JTextField(defaultReportPath, 20);
        controlPanel.add(reportPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        selectReportPathButton = new JButton("Selecionar Local");
        selectReportPathButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            fileChooser.setSelectedFile(new File(REPORT_FILE_NAME));
            int result = fileChooser.showSaveDialog(PlagiarismDetector.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                reportPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        controlPanel.add(selectReportPathButton, gbc);

        // Similarity threshold field
        gbc.gridx = 0;
        gbc.gridy = 2;
        controlPanel.add(new JLabel("Limiar de similaridade (%):"), gbc);

        gbc.gridx = 1;
        thresholdField = new JTextField(String.valueOf(DEFAULT_SIMILARITY_THRESHOLD * 100), 5);
        controlPanel.add(thresholdField, gbc);

        // Start analysis button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        startButton = new JButton("Iniciar Análise");
        startButton.addActionListener(e -> startAnalysis());
        controlPanel.add(startButton, gbc);

        add(controlPanel, BorderLayout.NORTH);

        // Results area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        setVisible(true);

        // Set the custom icon for the JFrame window
        try {
            // Use ClassLoader.getSystemResource to load the icon
            java.net.URL iconURL = ClassLoader.getSystemResource("app-icon.png");
            if (iconURL == null) {
                throw new IOException("Recurso 'app-icon.png' não encontrado no classpath. Verifique se o arquivo está em src/main/resources e foi incluído no JAR.");
            }
            System.out.println("Icon URL: " + iconURL); // Debug log to see the URL
            ImageIcon icon = new ImageIcon(iconURL);
            if (icon.getImageLoadStatus() != java.awt.MediaTracker.COMPLETE) {
                throw new IOException("Falha ao carregar a imagem. Status: " + icon.getImageLoadStatus());
            }
            setIconImage(icon.getImage());
            System.out.println("Ícone carregado com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao carregar o ícone: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startAnalysis() {
        // Clear the results area
        resultArea.setText("");

        // Validate and parse the similarity threshold
        double similarityThreshold;
        try {
            similarityThreshold = Double.parseDouble(thresholdField.getText()) / 100.0;
            if (similarityThreshold < 0 || similarityThreshold > 1) {
                throw new NumberFormatException("O limiar deve estar entre 0 e 100%.");
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Por favor, insira um valor válido para o limiar (0-100%). Usando o padrão: 4%.", "Erro", JOptionPane.ERROR_MESSAGE);
            similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
            thresholdField.setText(String.valueOf(DEFAULT_SIMILARITY_THRESHOLD * 100));
        }

        // Validate the folder path
        String folderPath = folderPathField.getText();
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "A pasta especificada não existe ou não é válida.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate the report path
        String reportPath = reportPathField.getText();
        File reportFile = new File(reportPath);
        File reportParentDir = reportFile.getParentFile();
        if (reportParentDir == null || (!reportParentDir.exists() && !reportParentDir.mkdirs())) {
            JOptionPane.showMessageDialog(this, "O diretório para salvar o relatório não é válido.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable the button while processing
        startButton.setEnabled(false);
        resultArea.append("Iniciando análise...\n");

        // Run the analysis in a separate thread to avoid freezing the GUI
        double finalSimilarityThreshold = similarityThreshold;
        new Thread(() -> {
            try {
                List<File> pdfFiles = new ArrayList<>();
                collectPDFFiles(folder, pdfFiles);

                if (pdfFiles.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        resultArea.append("Nenhum arquivo PDF encontrado na pasta ou subpastas: " + folderPath + "\n");
                        startButton.setEnabled(true);
                    });
                    return;
                }

                // Process each PDF
                List<PDFInfo> pdfInfos = new ArrayList<>();
                for (File file : pdfFiles) {
                    try (PDDocument document = PDDocument.load(file)) {
                        PDDocumentInformation info = document.getDocumentInformation();
                        String author = info.getAuthor();
                        String title = info.getTitle();
                        PDFTextStripper stripper = new PDFTextStripper();
                        String text = stripper.getText(document);
                        pdfInfos.add(new PDFInfo(file.getName(), file.getAbsolutePath(), author, title, text));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> resultArea.append("Erro ao processar " + file.getName() + ": " + e.getMessage() + "\n"));
                    }
                }

                // Compare all pairs of PDFs
                List<PlagiarismResult> results = new ArrayList<>();
                for (int i = 0; i < pdfInfos.size(); i++) {
                    for (int j = i + 1; j < pdfInfos.size(); j++) {
                        PDFInfo pdf1 = pdfInfos.get(i);
                        PDFInfo pdf2 = pdfInfos.get(j);

                        double similarity = calculateJaccardSimilarity(pdf1.nGrams, pdf2.nGrams);
                        boolean hasPlagiarism = similarity >= finalSimilarityThreshold;

                        List<String> mergedExcerpts = new ArrayList<>();
                        if (hasPlagiarism) {
                            List<String> commonNGrams = new ArrayList<>(pdf1.nGrams);
                            commonNGrams.retainAll(pdf2.nGrams);
                            mergedExcerpts = mergeConsecutiveNGrams(commonNGrams, pdf1.nGrams);
                        }

                        results.add(new PlagiarismResult(pdf1.fileName, pdf1.filePath, pdf2.fileName, pdf2.filePath, mergedExcerpts, similarity, hasPlagiarism));
                    }
                }

                // Generate and save the report
                try (FileWriter writer = new FileWriter(reportPath)) {
                    writer.write("Relatório de Plágio\n");
                    writer.write("===================\n\n");

                    writer.write("Arquivos processados:\n");
                    for (PDFInfo pdf : pdfInfos) {
                        writer.write("- " + pdf.fileName + " (Caminho: " + pdf.filePath + ")\n");
                    }
                    writer.write("\n");

                    writer.write("Resultados de Comparação:\n");
                    boolean plagiarismFound = false;
                    for (PlagiarismResult result : results) {
                        PDFInfo pdf1 = pdfInfos.stream()
                                .filter(p -> p.fileName.equals(result.file1))
                                .findFirst().orElse(null);
                        PDFInfo pdf2 = pdfInfos.stream()
                                .filter(p -> p.fileName.equals(result.file2))
                                .findFirst().orElse(null);

                        if (pdf1 != null && pdf2 != null) {
                            if (result.hasPlagiarism) {
                                plagiarismFound = true;
                                writer.write(String.format("Possível plágio detectado (%.2f%% de similaridade):\n", result.similarity * 100));
                                writer.write("Arquivo 1: " + pdf1.fileName + " (Caminho: " + pdf1.filePath + ")\n");
                                writer.write("Autor: " + pdf1.author + ", Título: " + pdf1.title + "\n");
                                writer.write("Arquivo 2: " + pdf2.fileName + " (Caminho: " + pdf2.filePath + ")\n");
                                writer.write("Autor: " + pdf2.author + ", Título: " + pdf2.title + "\n");

                                writer.write("Trechos copiados:\n");
                                int displayedExcerpts = 0;
                                for (String excerpt : result.copiedExcerpts) {
                                    if (displayedExcerpts >= MAX_EXCERPTS_TO_DISPLAY) {
                                        writer.write("(... mais trechos idênticos encontrados)\n");
                                        break;
                                    }
                                    writer.write("- " + excerpt + "\n");
                                    displayedExcerpts++;
                                }
                                writer.write("----------------------------------------\n\n");

                                // Display in the GUI (summary)
                                String resultText = String.format("Plágio detectado (%.2f%%): %s e %s\n", result.similarity * 100, pdf1.fileName, pdf2.fileName);
                                SwingUtilities.invokeLater(() -> resultArea.append(resultText));
                            } else {
                                writer.write(String.format("Nenhum plágio detectado entre %s e %s (%.2f%% de similaridade)\n",
                                        pdf1.fileName, pdf2.fileName, result.similarity * 100));
                                writer.write("----------------------------------------\n\n");
                            }
                        }
                    }

                    if (!plagiarismFound) {
                        writer.write("Nenhum caso de plágio foi detectado.\n");
                        SwingUtilities.invokeLater(() -> resultArea.append("Nenhum caso de plágio foi detectado.\n"));
                    }
                }

                // Open the report file automatically
                SwingUtilities.invokeLater(() -> {
                    try {
                        Desktop.getDesktop().open(new File(reportPath));
                        resultArea.append("Análise concluída. Relatório salvo em: " + reportPath + "\n");
                    } catch (IOException ex) {
                        resultArea.append("Erro ao abrir o relatório: " + ex.getMessage() + "\n");
                    }
                    startButton.setEnabled(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    resultArea.append("Erro durante a análise: " + ex.getMessage() + "\n");
                    startButton.setEnabled(true);
                });
            }
        }).start();
    }

    // Collect all PDFs, including subfolders
    private static void collectPDFFiles(File directory, List<File> pdfFiles) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        collectPDFFiles(file, pdfFiles);
                    } else if (file.getName().toLowerCase().endsWith(".pdf")) {
                        pdfFiles.add(file);
                    }
                }
            }
        }
    }

    // Generate n-grams from the text
    private static List<String> generateNGrams(String text) {
        List<String> nGrams = new ArrayList<>();
        String[] words = tokenize(text);

        for (int i = 0; i <= words.length - NGRAM_SIZE; i++) {
            StringBuilder nGram = new StringBuilder();
            for (int j = 0; j < NGRAM_SIZE; j++) {
                nGram.append(words[i + j]).append(" ");
            }
            nGrams.add(nGram.toString().trim());
        }
        return nGrams;
    }

    // Merge consecutive n-grams into larger excerpts
    private static List<String> mergeConsecutiveNGrams(List<String> commonNGrams, List<String> originalNGrams) {
        if (commonNGrams.isEmpty()) return new ArrayList<>();

        List<String> sortedNGrams = new ArrayList<>(new HashSet<>(commonNGrams));
        sortedNGrams.sort(Comparator.comparingInt(originalNGrams::indexOf));

        List<String> mergedExcerpts = new ArrayList<>();
        StringBuilder currentExcerpt = new StringBuilder(sortedNGrams.get(0));
        int lastIndex = originalNGrams.indexOf(sortedNGrams.get(0));

        for (int i = 1; i < sortedNGrams.size(); i++) {
            String nGram = sortedNGrams.get(i);
            int currentIndex = originalNGrams.indexOf(nGram);

            if (currentIndex == lastIndex + 1) {
                String[] words = nGram.split("\\s+");
                currentExcerpt.append(" ").append(words[words.length - 1]);
            } else {
                mergedExcerpts.add(currentExcerpt.toString());
                currentExcerpt = new StringBuilder(nGram);
            }
            lastIndex = currentIndex;
        }

        mergedExcerpts.add(currentExcerpt.toString());
        return mergedExcerpts;
    }

    // Calculate Jaccard similarity between two sets of n-grams
    private static double calculateJaccardSimilarity(List<String> nGrams1, List<String> nGrams2) {
        Set<String> set1 = new HashSet<>(nGrams1);
        Set<String> set2 = new HashSet<>(nGrams2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // Tokenize the text into words
    private static String[] tokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .split("\\s+");
    }

    public static void main(String[] args) {
        // Suppress PDFBox warnings
        Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.SEVERE);

        SwingUtilities.invokeLater(PlagiarismDetector::new);
    }
}