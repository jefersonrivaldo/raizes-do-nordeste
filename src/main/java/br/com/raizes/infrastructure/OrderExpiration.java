package br.com.raizes.infrastructure;

import br.com.raizes.application.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class OrderExpiration {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiration.class);
  private final Services services;

  public OrderExpiration(Services services) {
    this.services = services;
  }

  @Scheduled(
      fixedDelayString = "${app.order-expiration-scan-ms:60000}",
      initialDelayString = "${app.order-expiration-scan-ms:60000}")
  public void expireReservations() {
    for (long id : services.expiredOrderIds()) {
      try {
        // Each invocation commits independently; one failure does not roll back the batch.
        services.expireOrder(id);
      } catch (RuntimeException exception) {
        log.warn("Não foi possível expirar a reserva do pedido {}.", id, exception);
      }
    }
  }
}
