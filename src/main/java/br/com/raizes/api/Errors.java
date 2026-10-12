package br.com.raizes.api;

import br.com.raizes.api.Contracts.*;
import br.com.raizes.domain.Rules.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class Errors {
  private ResponseEntity<ErrorResponse> error(
      HttpServletRequest r, int status, String code, String message, List<ErrorDetail> details) {
    return error(r, status, code, message, details, HttpHeaders.EMPTY);
  }

  private ResponseEntity<ErrorResponse> error(
      HttpServletRequest r,
      int status,
      String code,
      String message,
      List<ErrorDetail> details,
      HttpHeaders headers) {
    return ResponseEntity.status(status)
        .headers(headers)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                code,
                message,
                details,
                Instant.now().toString(),
                r.getRequestURI(),
                r.getAttribute("requestId") == null
                    ? UUID.randomUUID().toString()
                    : (String) r.getAttribute("requestId")));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorResponse> business(BusinessException e, HttpServletRequest r) {
    return error(r, e.status, e.code, e.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(
      MethodArgumentNotValidException e, HttpServletRequest r) {
    return error(
        r,
        422,
        "VALIDACAO",
        "Dados inválidos.",
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new ErrorDetail(f.getField(), f.getDefaultMessage()))
            .toList());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ErrorResponse> malformed(Exception e, HttpServletRequest r) {
    return error(r, 422, "VALIDACAO", "Corpo, parâmetros ou cabeçalhos inválidos.", List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> methodNotSupported(
      HttpRequestMethodNotSupportedException e, HttpServletRequest r) {
    return error(
        r, 405, "METODO_NAO_PERMITIDO", "Método HTTP não permitido.", List.of(), e.getHeaders());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e, HttpServletRequest r) {
    return error(
        r,
        415,
        "TIPO_MIDIA_NAO_SUPORTADO",
        "Tipo de mídia do corpo não suportado.",
        List.of(),
        e.getHeaders());
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ErrorResponse> mediaTypeNotAcceptable(
      HttpMediaTypeNotAcceptableException e, HttpServletRequest r) {
    return error(
        r,
        406,
        "TIPO_MIDIA_NAO_ACEITAVEL",
        "Tipo de mídia solicitado não disponível.",
        List.of(),
        e.getHeaders());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> denied(Exception e, HttpServletRequest r) {
    return error(r, 403, "SEM_PERMISSAO", "Perfil sem permissão.", List.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> integrity(Exception e, HttpServletRequest r) {
    return error(r, 409, "CONFLITO", "Operação viola uma restrição de integridade.", List.of());
  }

  @ExceptionHandler(CannotAcquireLockException.class)
  ResponseEntity<ErrorResponse> busy(Exception e, HttpServletRequest r) {
    return error(r, 503, "BANCO_OCUPADO", "Tente novamente.", List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> missing(Exception e, HttpServletRequest r) {
    return error(r, 404, "NAO_ENCONTRADO", "Recurso não encontrado.", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> internal(Exception e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error(
            "Falha interna requestId={} tipo={}",
            r.getAttribute("requestId"),
            e.getClass().getSimpleName());
    return error(r, 500, "ERRO_INTERNO", "Erro interno.", List.of());
  }
}
