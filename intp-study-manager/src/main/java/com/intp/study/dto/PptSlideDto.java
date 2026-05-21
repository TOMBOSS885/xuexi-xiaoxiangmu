package com.intp.study.dto;

public record PptSlideDto(
        int slideNumber,
        String title,
        String slideText,
        String notes,
        String imagePath
) {
    public PptSlideDto {
        title = valueOrBlank(title);
        slideText = valueOrBlank(slideText);
        notes = valueOrBlank(notes);
        imagePath = valueOrBlank(imagePath);
    }

    public PptSlideDto withImagePath(String path) {
        return new PptSlideDto(slideNumber, title, slideText, notes, path);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
