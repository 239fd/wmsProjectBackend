package by.bsuir.organizationservice.model.event;

import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class OrganizationEvents {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationCreatedEvent {
        private String name;
        private String shortName;
        private String unp;
        private String address;
        private OrganizationStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationUpdatedEvent {
        private String name;
        private String shortName;
        private String unp;
        private String address;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationStatusChangedEvent {
        private OrganizationStatus oldStatus;
        private OrganizationStatus newStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationDeletedEvent {
        private String name;
        private String unp;
        private String deletedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvitationCodeGeneratedEvent {
        private String invitationCode;
        private String warehouseId;
        private String expiresAt;
    }
}
