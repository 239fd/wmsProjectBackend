package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;

    @Column(name = "records_found")
    private Integer recordsFound;

    @Column(name = "records_new")
    private Integer recordsNew;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}