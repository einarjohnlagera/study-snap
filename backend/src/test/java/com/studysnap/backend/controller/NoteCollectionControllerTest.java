package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.AdoptStudyPlanResponse;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CreateNoteCollectionRequest;
import com.studysnap.backend.dto.NoteCollectionDetailResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.NoteConceptCountsResponse;
import com.studysnap.backend.dto.PlanReadinessResponse;
import com.studysnap.backend.dto.GoalCollectionDetailResponse;
import com.studysnap.backend.dto.SetNoteCollectionChildrenOrderRequest;
import com.studysnap.backend.dto.SetNoteCollectionParentRequest;
import com.studysnap.backend.dto.SetNoteCollectionOrderRequest;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.dto.UpdateCollectionVisibilityRequest;
import com.studysnap.backend.dto.UpdateNoteCollectionRequest;
import com.studysnap.backend.entity.CollectionVisibility;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteCollectionControllerTest {

    private static final String PREAUTHORIZE_ROLES = "hasAnyRole('USER','ADMIN')";
    private static final String PREAUTHORIZE_ADMIN = "hasRole('ADMIN')";
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
                .getMethod("getReadiness", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("getNoteConceptCounts", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("getGoal", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("updateMetadata", String.class, UpdateNoteCollectionRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("updateParent", String.class, SetNoteCollectionParentRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("setPrimary", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("clearPrimary", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("setCompanion", String.class, CompanionContent.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("clearCompanion", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("clearTargetDate", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("setChildrenOrder", String.class, SetNoteCollectionChildrenOrderRequest.class, AuthenticatedUser.class)
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
        assertThat(NoteCollectionController.class
                .getMethod("adopt", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("adoptGoal", String.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ROLES);
        assertThat(NoteCollectionController.class
                .getMethod("updateVisibility", String.class, UpdateCollectionVisibilityRequest.class, AuthenticatedUser.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo(PREAUTHORIZE_ADMIN);
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
    void getReadiness_returnsCollectionReadiness() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        PlanReadinessResponse response = new PlanReadinessResponse(
                UUID.fromString(COLLECTION_ID),
                2,
                1,
                50,
                2,
                1,
                0,
                1,
                List.of(new SubjectProgressEntry("Biology", 2, 1, 0, 1, 50))
        );
        when(service.getReadiness(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        PlanReadinessResponse result = controller.getReadiness(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).getReadiness(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void getNoteConceptCounts_returnsPerNoteCounts() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        Map<String, NoteConceptCountsResponse> response = Map.of(
                NOTE_ID,
                new NoteConceptCountsResponse(3, 1, 1, 1)
        );
        when(service.getNoteConceptCounts(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        Map<String, NoteConceptCountsResponse> result = controller.getNoteConceptCounts(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).getNoteConceptCounts(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void patch_returnsUpdatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        UpdateNoteCollectionRequest request = new UpdateNoteCollectionRequest("Updated", null, null, 3, null);
        NoteCollectionDetailResponse response = detailResponse();
        when(service.updateMetadata(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.updateMetadata(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).updateMetadata(UUID.fromString(COLLECTION_ID), user.userId(), request);
    }

    @Test
    void updateParent_delegatesParentRequest() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        SetNoteCollectionParentRequest request = new SetNoteCollectionParentRequest(UUID.randomUUID());
        NoteCollectionDetailResponse response = detailResponse();
        when(service.updateParent(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.updateParent(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).updateParent(UUID.fromString(COLLECTION_ID), user.userId(), request);
    }

    @Test
    void setPrimary_returnsNoContent() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();

        ResponseEntity<Void> result = controller.setPrimary(COLLECTION_ID, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).setPrimary(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void clearPrimary_returnsNoContent() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();

        ResponseEntity<Void> result = controller.clearPrimary(COLLECTION_ID, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).clearPrimary(user.userId());
    }

    @Test
    void setCompanion_delegatesToServiceAndReturnsUpdatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        CompanionContent content = companionContent();
        NoteCollectionDetailResponse response = detailResponse();
        when(service.setCompanion(UUID.fromString(COLLECTION_ID), user.userId(), content)).thenReturn(response);

        NoteCollectionDetailResponse result = controller.setCompanion(COLLECTION_ID, content, user);

        assertThat(result).isEqualTo(response);
        verify(service).setCompanion(UUID.fromString(COLLECTION_ID), user.userId(), content);
    }

    @Test
    void clearCompanion_delegatesToServiceAndReturnsUpdatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        NoteCollectionDetailResponse response = detailResponse();
        when(service.clearCompanion(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        NoteCollectionDetailResponse result = controller.clearCompanion(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).clearCompanion(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void clearTargetDate_delegatesToServiceAndReturnsUpdatedCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        NoteCollectionDetailResponse response = detailResponse();
        when(service.clearTargetDate(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        NoteCollectionDetailResponse result = controller.clearTargetDate(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).clearTargetDate(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void listPublic_returnsPublishedCollectionsWithoutAuthentication() {
        NoteCollectionController controller = new NoteCollectionController(service);
        NoteCollectionSummaryResponse response = summaryResponse();
        when(service.listPublic("LET")).thenReturn(List.of(response));

        List<NoteCollectionSummaryResponse> result = controller.listPublic("LET");

        assertThat(result).containsExactly(response);
        verify(service).listPublic("LET");
    }

    @Test
    void getPublic_returnsPublishedCollectionWithoutAuthentication() {
        NoteCollectionController controller = new NoteCollectionController(service);
        NoteCollectionDetailResponse response = detailResponse();
        when(service.getPublic(UUID.fromString(COLLECTION_ID))).thenReturn(response);

        NoteCollectionDetailResponse result = controller.getPublic(COLLECTION_ID);

        assertThat(result).isEqualTo(response);
        verify(service).getPublic(UUID.fromString(COLLECTION_ID));
    }

    @Test
    void updateVisibility_delegatesAdminPublishRequest() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        UpdateCollectionVisibilityRequest request = new UpdateCollectionVisibilityRequest(CollectionVisibility.PUBLIC.name());
        NoteCollectionDetailResponse response = detailResponse();
        when(service.updateVisibility(UUID.fromString(COLLECTION_ID), user.userId(), request.visibility()))
                .thenReturn(response);

        NoteCollectionDetailResponse result = controller.updateVisibility(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).updateVisibility(UUID.fromString(COLLECTION_ID), user.userId(), request.visibility());
    }

    @Test
    void adopt_returnsPersonalStudyPlanId() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        AdoptStudyPlanResponse response = new AdoptStudyPlanResponse(UUID.fromString(COLLECTION_ID), 3, 0, false);
        when(service.adopt(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        AdoptStudyPlanResponse result = controller.adopt(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).adopt(UUID.fromString(COLLECTION_ID), user.userId());
    }

    @Test
    void adoptGoal_returnsPersonalGoalId() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        AdoptGoalResponse response = new AdoptGoalResponse(UUID.fromString(COLLECTION_ID), 2, 0, 6, 1, false);
        when(service.adoptGoal(UUID.fromString(COLLECTION_ID), user.userId())).thenReturn(response);

        AdoptGoalResponse result = controller.adoptGoal(COLLECTION_ID, user);

        assertThat(result).isEqualTo(response);
        verify(service).adoptGoal(UUID.fromString(COLLECTION_ID), user.userId());
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
    void childrenOrder_returnsGoalCollection() {
        NoteCollectionController controller = new NoteCollectionController(service);
        AuthenticatedUser user = authenticatedUser();
        UUID childId = UUID.randomUUID();
        SetNoteCollectionChildrenOrderRequest request = new SetNoteCollectionChildrenOrderRequest(List.of(childId));
        GoalCollectionDetailResponse response = goalDetailResponse();
        when(service.setChildrenOrder(UUID.fromString(COLLECTION_ID), user.userId(), request)).thenReturn(response);

        GoalCollectionDetailResponse result = controller.setChildrenOrder(COLLECTION_ID, request, user);

        assertThat(result).isEqualTo(response);
        verify(service).setChildrenOrder(UUID.fromString(COLLECTION_ID), user.userId(), request);
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
        return new NoteCollectionSummaryResponse(
                UUID.fromString(COLLECTION_ID),
                COLLECTION_TITLE,
                null,
                CollectionVisibility.PRIVATE.name(),
                null,
                null,
                null,
                1,
                0,
                0,
                now,
                now
        );
    }

    private NoteCollectionDetailResponse detailResponse() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        return new NoteCollectionDetailResponse(
                UUID.fromString(COLLECTION_ID),
                COLLECTION_TITLE,
                null,
                CollectionVisibility.PRIVATE.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now,
                new com.studysnap.backend.dto.NoteCollectionProgressResponse(0, 0, 0),
                List.of()
        );
    }

    private GoalCollectionDetailResponse goalDetailResponse() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        return new GoalCollectionDetailResponse(
                UUID.fromString(COLLECTION_ID),
                COLLECTION_TITLE,
                null,
                CollectionVisibility.PRIVATE.name(),
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                List.of(),
                now,
                now,
                List.of()
        );
    }

    private CompanionContent companionContent() {
        return new CompanionContent(
                "Overview",
                "Study strategy",
                "Common mistakes",
                List.of(new CompanionFaqItem("Question?", "Answer."))
        );
    }
}
