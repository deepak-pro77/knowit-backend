package com.knowitai.validation;

/** An image that passed validation, with a mime type WE detected (not the one the client claimed). */
public record ValidatedImage(byte[] data, String mimeType) { }
