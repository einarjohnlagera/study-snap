package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.NoteCollectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteCollectionControllerTest {

    private static final String PREAUTHORIZE_ROLES = "hasAnyRole('USER','ADMIN')";
    private static final String COLLECTION_ID = "e2163cd7-6bf7-45e9-8a01-14002a8fd8f6";
    private static final String NOTE_ID = "5940c881-7f8c-48cb-a00c-6ebe34872976";
    private static final String COLLECTION_TITLE = "Biology Unit";

    @Mock
    private NoteCollectionService service;

    @Test
    void endpoints_requireAuthenticatedUserRole() throws NoSuchMethodException {
        assertThat(NoteCollectionController.class
                .getMethod("list", AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("create", CreateNoteCollectionRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("get", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("updateMetadata", String.class, UpdateNoteCollectionRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("delete", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("addItems", String.class, AddNoteCollectionItemsRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("removeItem", String.class, String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("setOrder", String.class, SetNoteCollectionOrderRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
    }

    @Test
    void list_returnsCollectionsForAuthenticatedUser() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        NoteCollectionSummaryResponse response = summaryResponse();
        when(service.list(user.userId())).thenReturn(List.of(response));

        List<NoteCollectionSummaryResponse> result = controller.list(user);

        assertThat(result).containsExactly(response);
        verify(service).list(user.userId());
    }

    @Test
    void create_returnsCreatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest(COLLECTION_TITLE, null, List.of(UUID.fromString(NOTE_ID)));
        NoteCollectionDetailResponse response = detailResponse();
        when(service.create(user.userId(), request)).thenReturn(response);

        ResponseEntity<NoteCollectionDetailResponse> result = controller.create(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).create(user.userId(), request);
    }

    @Test
    void get_returnsCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        NoteCollectionDetailResponse response = detailResponse();
        when(service.get(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        NoteCollectionDetailResponse result = controller.get(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).get(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void patch_returnsUpdatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        UpdateNoteCollectionRequest request = new UpdateNoteCollectionRequest("Updated", null);
        NoteCollectionDetailResponse response = detailResponse();
        when(service.updateMetadata(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.updateMetadata(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).updateMetadata(UUID.fromString(COLLECTION_ID), user.userId(), request);
    }

    @Test
    void delete_returnsNoContent() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();

        ResponseEntity<Void> result = controller.delete(COLLECTION_ID, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void addItems_returnsCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        AddNoteCollectionItemsRequest request = new AddNoteCollectionItemsRequest(List.of(UUID.fromString(NOTE_ID)));
        NoteCollectionDetailResponse response = detailResponse();
        when(service.addItems(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.addItems(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).addItems(UUID.fromString(COLLECTION_ID), user.userId(), request);
    }

    @Test
    void removeItem_returnsNoContent() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();

        ResponseEntity<Void> result = controller.removeItem(COLLECTION_ID, NOTE_ID, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).removeItem(UUID.fromString(COLLECTION_ID), user.userId(), UUID.fromString(NOTE_ID));
    }

    @Test
    void order_returnsCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        SetNoteCollectionOrderRequest request = new SetNoteCollectionOrderRequest(List.of(
                new SetNoteCollectionOrderRequest.OrderedItem(UUID.fromString(NOTE_ID), "Week 1")
        ));
        NoteCollectionDetailResponse response = detailResponse();
        when(service.setOrder(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.setOrder(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).setOrder(UUID.fromString(COLLECTION_ID), user.userId(), request);
    }

    @Test
    void unknownCollectionExceptionCarriesNotFoundStatus() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        when(service.get(UUID.fromString(COLLECTION_ID), user.userId())).thenThrow(new CollectionNotFoundException());

        assertThatThrownBy(() -> controller.get(COLLECTION_ID, user))
                .isInstanceOf(CollectionNotFoundException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonOwnedCollectionExceptionCarriesNotFoundStatus() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        doThrow(new CollectionNotFoundException()).when(service).delete(UUID.fromString(COLLECTION_ID), user.userId());

        assertThatThrownBy(() -> controller.delete(COLLECTION_ID, user))
                .isInstanceOf(CollectionNotFoundException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blankTitleExceptionCarriesBadRequestStatus() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        CreateNoteCollectionRequest request = new CreateNoteCollectionRequest(" ", null, null);
        when(service.create(user.userId(), request)).thenThrow(new InvalidCollectionRequestException("Collection title is required."));

        assertThatThrownBy(() -> controller.create(request, user))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedPathUuidThrowsCollectionNotFound() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();

        assertThatThrownBy(() -> controller.get("not-a-uuid", user))
                .isInstanceOf(CollectionNotFoundException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 0);
    }

    private NoteCollectionSummaryResponse summaryResponse() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        return new NoteCollectionSummaryResponse(UUID.fromString(COLLECTION_ID), COLLECTION_TITLE, null, 1, now, now);
    }

    private NoteCollectionDetailResponse detailResponse() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        return new NoteCollectionDetailResponse(
                UUID.fromString(COLLECTION_ID),
                COLLECTION_TITLE,
                null,
                now,
                now,
                new com.studysnap.backend.dto.NoteCollectionProgressResponse(0, 0, 0),
                List.of()
        );
    }
}
