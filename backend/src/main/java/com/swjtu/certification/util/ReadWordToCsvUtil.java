package com.swjtu.certification.util;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReadWordToCsvUtil {
    public static void readWord(String filePath, String csvPath) {
        File f = new File(filePath);
        if (!f.exists()){
            System.out.println("文件不存在: " + filePath);
            return ;
        }

        try (FileInputStream fis = new FileInputStream(f)) {
            if (filePath.toLowerCase().endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(fis)) {
                    List<XWPFTable> tables = doc.getTables();
                    if (tables.isEmpty()){
                        System.out.println("文档中没有表格");
                        return ;
                    }

                    int startIndex = -1;
                    for (int i = 0; i < tables.size(); i++) {
                        XWPFTable t = tables.get(i);
                        if (!t.getRows().isEmpty()) {
                            XWPFTableRow firstRow = t.getRow(0);
                            if (firstRow != null && !firstRow.getTableCells().isEmpty()) {
                                String firstText = firstRow.getCell(0).getText().trim();
                                if (firstText.contains("公共基础课程")) {
                                    startIndex = i;
                                    break;
                                }
                            }
                        }
                    }

                    if (startIndex == -1) {
                        System.out.println("未找到以公共基础课程开头的表格");
                        return ;
                    }

                    List<XWPFTable> targetTables = new ArrayList<>(tables.subList(startIndex, tables.size()));
                    List<XWPFTableRow> allRows = new ArrayList<>();
                    for (XWPFTable t : targetTables) {
                        allRows.addAll(t.getRows());
                    }

                    int rows = allRows.size();
                    int maxCols = 0;
                    for (XWPFTableRow row : allRows) {
                        int colCount = 0;
                        for (XWPFTableCell cell : row.getTableCells()) {
                            int span = getGridSpan(cell);
                            colCount += span;
                        }
                        if (colCount > maxCols) maxCols = colCount;
                    }

                    String[][] matrix = new String[rows][maxCols];
                    STMerge.Enum[][] vMerge = new STMerge.Enum[rows][maxCols];

                    for (int r = 0; r < rows; r++) {
                        XWPFTableRow row = allRows.get(r);
                        List<XWPFTableCell> cells = row.getTableCells();

                        int colIndex = 0;
                        for (XWPFTableCell cell : cells) {
                            while (colIndex < maxCols && matrix[r][colIndex] != null) colIndex++;

                            int span = getGridSpan(cell);
                            String text = getCellText(cell);
                            if (text != null) text = text.trim();
                            if (text != null && text.isEmpty()) text = null;

                            for (int k = 0; k < span && (colIndex + k) < maxCols; k++) {
                                matrix[r][colIndex + k] = text;
                            }

                            STMerge.Enum vm = getVMergeM(cell);
                            for (int k = 0; k < span && (colIndex + k) < maxCols; k++) {
                                vMerge[r][colIndex + k] = vm;
                            }

                            colIndex += span;
                        }
                    }

                    for (int c = 0; c < maxCols; c++) {
                        String lastVal = null;
                        for (int r = 0; r < rows; r++) {
                            STMerge.Enum vm = vMerge[r][c];
                            if (vm == STMerge.RESTART) {
                                lastVal = matrix[r][c];
                            } else if (vm == STMerge.CONTINUE) {
                                if (lastVal != null) {
                                    matrix[r][c] = lastVal;
                                }
                            } else {
                                lastVal = matrix[r][c];
                            }
                        }
                    }

                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < maxCols; c++) {
                            if (matrix[r][c] == null || matrix[r][c].trim().isEmpty()) {
                                matrix[r][c] = "null";
                            }
                        }
                    }

                    try (BufferedWriter csvWriter = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(csvPath), StandardCharsets.UTF_8))) {

                        for (int r = 0; r < rows; r++) {
                            StringBuilder rowBuilder = new StringBuilder();

                            for (int c = 0; c < maxCols; c++) {
                                String cellVal = matrix[r][c];
                                if (cellVal == null) cellVal = "";

                                String escaped = cellVal
                                        .replace("\"", "\"\"")
                                        .replace("\r", "")
                                        .replace("\n", "");

                                rowBuilder.append("\"").append(escaped).append("\"");

                                if (c < maxCols - 1) rowBuilder.append(",");
                            }

                            csvWriter.write(rowBuilder.toString());
                            csvWriter.newLine();
                        }
                    }

                    System.out.println("2 输出 CSV 完成 : " + csvPath);
                    return ;
                }
            } else {
                System.out.println("不支持的文件格式");
                return ;
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static String getCellText(XWPFTableCell cell) {
        if (cell == null) return null;
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : cell.getParagraphs()) {
            String t = p.getText();
            if (t != null && !t.trim().isEmpty()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(t);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static int getGridSpan(XWPFTableCell cell) {
        if (cell == null) return 1;
        try {
            if (cell.getCTTc() != null && cell.getCTTc().getTcPr() != null && cell.getCTTc().getTcPr().isSetGridSpan()) {
                return cell.getCTTc().getTcPr().getGridSpan().getVal().intValue();
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private static STMerge.Enum getVMergeM(XWPFTableCell cell) {
        if (cell == null || cell.getCTTc() == null || cell.getCTTc().getTcPr() == null) return null;

        CTVMerge vmObj = cell.getCTTc().getTcPr().getVMerge();
        if (vmObj == null) return null;

        if (vmObj.isSetVal()) {
            return vmObj.getVal();
        }
        return STMerge.CONTINUE;
    }
}
