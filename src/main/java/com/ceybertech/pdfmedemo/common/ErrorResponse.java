package com.ceybertech.pdfmedemo.common;

/** Every error response has this shape: { "message": "Human readable reason" }. */
public record ErrorResponse(String message) {
}
