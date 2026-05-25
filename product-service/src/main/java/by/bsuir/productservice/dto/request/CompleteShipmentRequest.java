package by.bsuir.productservice.dto.request;

import java.time.LocalDate;

public record CompleteShipmentRequest(
        String vehicleMake,
        String vehicleNumber,
        String trailerNumber,
        String driverName,
        String proxyNumber,
        LocalDate proxyDate,
        String proxyIssuedBy,
        String sealNumber,
        String contractNumber,
        LocalDate contractDate,
        String accompanyingDocs,
        String carrierName,
        String carrierInn,
        String carrierAddress,
        String carrierPhone,
        String countryOfManufacture,
        Integer loadingArrivalHour,
        Integer loadingArrivalMin,
        Integer loadingDepartureHour,
        Integer loadingDepartureMin,
        Integer unloadingArrivalHour,
        Integer unloadingArrivalMin,
        Integer unloadingDepartureHour,
        Integer unloadingDepartureMin,
        String paymentTerms,
        String specialTerms,
        String shipperInstructions,
        String carrierRemarks
) {}
