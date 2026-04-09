package com.compliance.automation.parser;

import java.io.File;
import java.io.IOException;

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
			log.debug("PDF loaded successfully with {} pages", totalPages);

			PDFTextStripper stripper = new PDFTextStripper();
			StringBuilder extractedText = new StringBuilder(Math.max(1024, totalPages * 512));

			for (int startPage = 1; startPage <= totalPages; startPage += PAGE_BATCH_SIZE) {
				int endPage = Math.min(startPage + PAGE_BATCH_SIZE - 1, totalPages);
				stripper.setStartPage(startPage);
				stripper.setEndPage(endPage);
				extractedText.append(stripper.getText(document));
				log.debug("Extracted PDF text for page range {}-{}", startPage, endPage);
			}

			log.info("Completed PDF text extraction for file={} (pages={}, chars={})",
					file.getName(), totalPages, extractedText.length());
			return extractedText.toString();
		} catch (IOException exception) {
			log.error("Failed to extract text from PDF file={}", file.getAbsolutePath(), exception);
			throw new RuntimeException("Failed to extract text from PDF", exception);
		}
	}
}
