package org.openlmis.requisition.service;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.requisition.domain.Requisition;
import org.openlmis.requisition.domain.RequisitionLineItem;
import org.openlmis.requisition.domain.RequisitionStatus;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProcessingScheduleDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.SupervisoryNodeDto;
import org.openlmis.requisition.dto.UserDto;
import org.openlmis.requisition.exception.RequisitionException;
import org.openlmis.requisition.repository.RequisitionLineItemRepository;
import org.openlmis.requisition.repository.RequisitionRepository;
import org.openlmis.requisition.service.referencedata.PeriodReferenceDataService;
import org.openlmis.requisition.service.referencedata.ProgramReferenceDataService;
import org.openlmis.requisition.service.referencedata.ScheduleReferenceDataService;
import org.openlmis.requisition.service.referencedata.SupervisoryNodeReferenceDataService;
import org.openlmis.requisition.service.referencedata.UserReferenceDataService;
import org.openlmis.settings.service.ConfigurationSettingService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.UnusedPrivateField"})
@RunWith(MockitoJUnitRunner.class)
public class RequisitionServiceTest {

  private Requisition requisition;

  @Mock
  private ProgramDto program;

  @Mock
  private ProcessingPeriodDto period;

  @Mock
  private ProcessingScheduleDto schedule;

  @Mock
  private SupervisoryNodeDto supervisoryNode;

  @Mock
  private RequisitionLineItemService requisitionLineItemService;

  @Mock
  private RequisitionLineItemRepository requisitionLineItemRepository;

  @Mock
  private ConfigurationSettingService configurationSettingService;

  @Mock
  private RequisitionRepository requisitionRepository;

  @Mock
  private UserReferenceDataService userReferenceDataService;

  @Mock
  private ProgramReferenceDataService programReferenceDataService;

  @Mock
  private SupervisoryNodeReferenceDataService supervisoryNodeReferenceDataService;

  @Mock
  private PeriodReferenceDataService periodReferenceDataService;

  @Mock
  private ScheduleReferenceDataService scheduleReferenceDataService;

  @InjectMocks
  private RequisitionService requisitionService;

  @Before
  public void setUp() {
    generateRequisition();
    mockRepositories();
  }

  @Test
  public void shouldDeleteRequisitionIfItIsInitiated() throws RequisitionException {
    requisition.setStatus(RequisitionStatus.INITIATED);
    boolean deleted = requisitionService.tryDelete(requisition.getId());

    assertTrue(deleted);
  }

  @Test
  public void shouldNotDeleteRequisitionWhenStatusIsSubmitted() throws RequisitionException {
    requisition.setStatus(RequisitionStatus.SUBMITTED);
    boolean deleted = requisitionService.tryDelete(requisition.getId());

    assertFalse(deleted);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenDeletingNotExistingRequisition()
        throws RequisitionException {
    UUID deletedRequisitionId = requisition.getId();
    when(requisitionRepository
          .findOne(requisition.getId()))
          .thenReturn(null);
    requisitionService.tryDelete(deletedRequisitionId);
  }

  @Test
  public void shouldSkipRequisitionIfItIsValid() throws RequisitionException {
    when(program.getPeriodsSkippable()).thenReturn(true);
    Requisition skippedRequisition = requisitionService.skip(requisition.getId());

    assertEquals(skippedRequisition.getStatus(), RequisitionStatus.SKIPPED);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenSkippingNotSkippableProgram()
        throws RequisitionException {
    when(program.getPeriodsSkippable()).thenReturn(false);
    requisitionService.skip(requisition.getId());
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenSkippingNotExistingRequisition()
        throws RequisitionException {
    when(requisitionRepository
          .findOne(requisition.getId()))
          .thenReturn(null);
    requisitionService.skip(requisition.getId());
  }

  @Test
  public void shouldRejectRequisitionIfRequisitionStatusIsAuthorized() throws RequisitionException {
    requisition.setStatus(RequisitionStatus.AUTHORIZED);
    Requisition returnedRequisition = requisitionService.reject(requisition.getId());

    assertEquals(returnedRequisition.getStatus(), RequisitionStatus.INITIATED);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenRejectingRequisitionWithStatusApproved()
        throws RequisitionException {
    requisition.setStatus(RequisitionStatus.APPROVED);
    requisitionService.reject(requisition.getId());
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenRejectingNotExistingRequisition()
        throws RequisitionException {
    when(requisitionRepository.findOne(requisition.getId())).thenReturn(null);
    requisitionService.reject(requisition.getId());
  }

  @Test
  @Ignore
  public void shouldGetAuthorizedRequisitionsIfSupervisoryNodeProvided() {

    requisition.setStatus(RequisitionStatus.AUTHORIZED);
    requisition.setSupervisoryNode(supervisoryNode.getId());

    when(requisitionRepository
          .searchRequisitions(null, null, null, null, null, supervisoryNode.getId(), null))
          .thenReturn(Arrays.asList(requisition));

    List<Requisition> authorizedRequisitions =
          requisitionService.getAuthorizedRequisitions(supervisoryNode);
    List<Requisition> expected = Arrays.asList(requisition);

    assertEquals(expected, authorizedRequisitions);
  }

  @Test
  @Ignore
  public void shouldGetRequisitionsForApprovalIfUserHasSupervisedNode() {

    UUID supervisoryNodeId = UUID.randomUUID();
    requisition.setSupervisoryNode(supervisoryNodeId);
    requisition.setStatus(RequisitionStatus.AUTHORIZED);
    UserDto user = mock(UserDto.class);

    //when(user.getSupervisedNode()).thenReturn(supervisoryNodeId);
    when(supervisoryNode.getId()).thenReturn(supervisoryNodeId);
    when(userReferenceDataService.findOne(user.getId()))
          .thenReturn(user);
    when(supervisoryNodeReferenceDataService.findOne(supervisoryNodeId))
          .thenReturn(supervisoryNode);
    when(requisitionRepository
          .searchRequisitions(null, null, null, null, null, supervisoryNodeId, null))
          .thenReturn(Arrays.asList(requisition));

    List<Requisition> requisitionsForApproval =
          requisitionService.getRequisitionsForApproval(user.getId());

    assertEquals(1, requisitionsForApproval.size());
    assertEquals(requisitionsForApproval.get(0), requisition);
  }

  @Ignore
  @Test
  public void shouldInitiateRequisitionIfItNotAlreadyExist() throws RequisitionException {
    requisition.setStatus(null);
    when(requisitionRepository
          .findOne(requisition.getId()))
          .thenReturn(null);
    when(program.getProcessingSchedule()).thenReturn(schedule);
    when(period.getProcessingSchedule()).thenReturn(schedule);
    Requisition initiatedRequisition = requisitionService.initiateRequisition(requisition);

    assertEquals(initiatedRequisition.getStatus(), RequisitionStatus.INITIATED);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenInitiatingEmptyRequisition()
        throws RequisitionException {
    requisitionService.initiateRequisition(null);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenInitiatingAlreadyExistingRequisition()
        throws RequisitionException {
    requisitionService.initiateRequisition(requisition);
  }

  @Test
  public void shouldReleaseRequisitionsAsOrder() throws RequisitionException {
    requisition.setStatus(RequisitionStatus.APPROVED);
    List<Requisition> requisitions = Arrays.asList(requisition);
    List<Requisition> expectedRequisitions = requisitionService
          .releaseRequisitionsAsOrder(requisitions);
    assertEquals(RequisitionStatus.RELEASED, expectedRequisitions.get(0).getStatus());
  }

  @Test
  public void shouldFindRequisitionIfItExists() {
    when(requisitionRepository.searchRequisitions(
          requisition.getFacility(),
          requisition.getProgram(),
          requisition.getCreatedDate().minusDays(2),
          requisition.getCreatedDate().plusDays(2),
          requisition.getProcessingPeriod(),
          requisition.getSupervisoryNode(),
          requisition.getStatus()))
          .thenReturn(Arrays.asList(requisition));

    List<Requisition> receivedRequisitions = requisitionService.searchRequisitions(
          requisition.getFacility(),
          requisition.getProgram(),
          requisition.getCreatedDate().minusDays(2),
          requisition.getCreatedDate().plusDays(2),
          requisition.getProcessingPeriod(),
          requisition.getSupervisoryNode(),
          requisition.getStatus());

    assertEquals(1, receivedRequisitions.size());
    assertEquals(
          receivedRequisitions.get(0).getFacility(),
          requisition.getFacility());
    assertEquals(
          receivedRequisitions.get(0).getProgram(),
          requisition.getProgram());
    assertTrue(
          receivedRequisitions.get(0).getCreatedDate().isAfter(
                requisition.getCreatedDate().minusDays(2)));
    assertTrue(
          receivedRequisitions.get(0).getCreatedDate().isBefore(
                requisition.getCreatedDate().plusDays(2)));
    assertEquals(
          receivedRequisitions.get(0).getProcessingPeriod(),
          requisition.getProcessingPeriod());
    assertEquals(
          receivedRequisitions.get(0).getSupervisoryNode(),
          requisition.getSupervisoryNode());
    assertEquals(
          receivedRequisitions.get(0).getStatus(),
          requisition.getStatus());
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenInitiatingRequisitionProgramIsNotLinkedWithASchedule()
        throws RequisitionException {
    requisition.setStatus(null);
    when(requisitionRepository
          .findOne(requisition.getId()))
          .thenReturn(null);
    requisitionService.initiateRequisition(requisition);
  }

  private Requisition generateRequisition() {
    requisition = new Requisition();
    requisition.setId(UUID.randomUUID());
    requisition.setCreatedDate(LocalDateTime.now());
    requisition.setStatus(RequisitionStatus.INITIATED);
    List<RequisitionLineItem> requisitionLineItems = new ArrayList<>();
    requisitionLineItems.add(mock(RequisitionLineItem.class));
    requisition.setRequisitionLineItems(requisitionLineItems);
    UUID programId = UUID.randomUUID();
    requisition.setProgram(programId);
    UUID processingPeriodId = UUID.randomUUID();
    requisition.setProcessingPeriod(processingPeriodId);
    return requisition;
  }

  private void mockRepositories() {
    when(requisitionRepository
          .findOne(requisition.getId()))
          .thenReturn(requisition);
    when(requisitionRepository
          .save(requisition))
          .thenReturn(requisition);
    when(programReferenceDataService
          .findOne(any()))
          .thenReturn(program);
    when(scheduleReferenceDataService
          .findOne(any()))
          .thenReturn(schedule);
  }
}
