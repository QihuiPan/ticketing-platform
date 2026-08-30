package com.portfolio.ticketing.api;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.TicketOrder;
import com.portfolio.ticketing.service.CurrentUserService;
import com.portfolio.ticketing.service.OrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class TicketController {

    private final OrderService orders;
    private final CurrentUserService currentUser;

    public TicketController(OrderService orders, CurrentUserService currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @GetMapping(value = "/{orderId}/ticket", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ticket(@PathVariable UUID orderId) {
        TicketOrder order = orders.ownedOrder(orderId, currentUser.id(), currentUser.isAdmin());
        if (order.getStatus() != DomainTypes.OrderStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_UNAVAILABLE",
                    "A ticket is available only for a confirmed order");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var matrix = new QRCodeWriter().encode(order.getTicketCode(), BarcodeFormat.QR_CODE, 320, 320);
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not generate ticket QR code", exception);
        }
    }
}
