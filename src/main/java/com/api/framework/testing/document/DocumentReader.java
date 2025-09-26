package com.api.framework.testing.document;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentReader {

    /**
     * Reads an Excel file and returns rows as List<Map<String, String>>.
     */
    public static List<Map<String, String>> readExcel(String filePath) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Read headers
            Row headerRow = rowIterator.next();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }

            // Read rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    rowData.put(headers.get(i), cell.toString());
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    /**
     * Reads CSV using simple Java (or you can swap with OpenCSV).
     */
    public static List<Map<String, String>> readCSV(String filePath) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filePath))) {
            String[] headers = scanner.nextLine().split(",");

            while (scanner.hasNextLine()) {
                String[] values = scanner.nextLine().split(",");
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    rowData.put(headers[i], values.length > i ? values[i] : "");
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    public static List<Map<String, String>> readDocx(String filePath) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            // 1️⃣ Read paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("paragraph", text);
                    rows.add(row);
                }
            }

            // 2️⃣ Read tables
            for (XWPFTable table : document.getTables()) {
                List<XWPFTableRow> tableRows = table.getRows();
                if (tableRows.isEmpty()) continue;

                // Use first row as header
                List<String> headers = tableRows.get(0).getTableCells().stream()
                        .map(cell -> cell.getText().trim())
                        .collect(Collectors.toList());

                // Read rest of the rows
                for (int i = 1; i < tableRows.size(); i++) {
                    XWPFTableRow row = tableRows.get(i);
                    Map<String, String> rowData = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        String cellText = row.getTableCells().size() > j ? row.getCell(j).getText() : "";
                        rowData.put(headers.get(j), cellText);
                    }
                    rows.add(rowData);
                }
            }
        }

        return rows;
    }

    public static List<String> readDoc(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(filePath))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (!p.getText().trim().isEmpty()) {
                    lines.add(p.getText());
                }
            }
        }
        return lines;
    }
}

