package com.compliance.automation.parser;

import java.io.File;
import java.io.IOException;

import com.compliance.automation.exception.FileProcessingException;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PDFParser {

	private static final Logger log = LoggerFactory.getLogger(PDFParser.class);
	private static final int PAGE_BATCH_SIZE = 25;

	public String extractText(File file) {
		if (file == null) {
			throw new IllegalArgumentException("PDF file must not be null");
		}
		if (!file.exists() || !file.isFile()) {
			throw new IllegalArgumentException("PDF file does not exist: " + file.getAbsolutePath());
		}

		log.info("Starting PDF text extraction for file={}", file.getAbsolutePath());

		try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
			int totalPages = document.getNumberOfPages();
			int totalBatches = totalPages == 0 ? 0 : ((totalPages - 1) / PAGE_BATCH_SIZE) + 1;
			log.info("PDF loaded successfully with {} pages (batchSize={}, batches={})",
					totalPages,
					PAGE_BATCH_SIZE,
					totalBatches);

			PDFTextStripper stripper = new PDFTextStripper();
			StringBuilder extractedText = new StringBuilder(Math.max(1024, totalPages * 512));

			for (int startPage = 1; startPage <= totalPages; startPage += PAGE_BATCH_SIZE) {
				int endPage = Math.min(startPage + PAGE_BATCH_SIZE - 1, totalPages);
				stripper.setStartPage(startPage);
				stripper.setEndPage(endPage);
				extractedText.append(stripper.getText(document));
			}

			String normalizedText = normalizeText(extractedText.toString());

			log.info("Completed PDF text extraction for file={} (pages={}, chars={})",
					file.getName(), totalPages, normalizedText.length());
			log.debug("Raw chars={}, normalized chars={}", extractedText.length(), normalizedText.length());
			return normalizedText;
		} catch (IOException exception) {
			log.error("Failed to extract text from PDF file={}", file.getAbsolutePath(), exception);
			throw new FileProcessingException("Failed to extract text from PDF.", exception);
		}
	}

	private String normalizeText(String rawText) {
		if (rawText == null || rawText.isEmpty()) {
			return "";
		}

		String normalizedNewLines = rawText.replace("\r\n", "\n").replace('\r', '\n');
		String[] lines = normalizedNewLines.split("\\n", -1);
		StringBuilder normalized = new StringBuilder(rawText.length());

		for (int i = 0; i < lines.length; i++) {
			normalized.append(lines[i].trim());
			if (i < lines.length - 1) {
				normalized.append(System.lineSeparator());
			}
		}

		return normalized.toString().trim();
	}
}
