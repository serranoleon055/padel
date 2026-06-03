package com.padel.rankpadel.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String objeto, Long id) {
        super(objeto + " no encontrado con id: " + id);
    }
}
