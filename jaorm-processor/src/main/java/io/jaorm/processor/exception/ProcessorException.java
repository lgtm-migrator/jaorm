package io.jaorm.processor.exception;

public class ProcessorException extends RuntimeException {

    public ProcessorException(String exception) {
        super(exception);
    }

    public ProcessorException(Throwable ex) {
        super(ex);
    }
}