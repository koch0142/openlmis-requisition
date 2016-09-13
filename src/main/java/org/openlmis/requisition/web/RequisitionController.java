package org.openlmis.requisition.web;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

import org.openlmis.hierarchyandsupervision.domain.SupervisoryNode;
import org.openlmis.hierarchyandsupervision.domain.User;
import org.openlmis.hierarchyandsupervision.utils.ErrorResponse;
import org.openlmis.referencedata.domain.Facility;
import org.openlmis.referencedata.domain.ProcessingPeriod;
import org.openlmis.referencedata.domain.Program;
import org.openlmis.referencedata.web.BaseController;
import org.openlmis.requisition.domain.Comment;
import org.openlmis.requisition.domain.Requisition;
import org.openlmis.requisition.domain.RequisitionStatus;
import org.openlmis.requisition.exception.RequisitionException;
import org.openlmis.requisition.repository.CommentRepository;
import org.openlmis.requisition.repository.RequisitionRepository;
import org.openlmis.requisition.service.RequisitionService;
import org.openlmis.requisition.validate.RequisitionValidator;
import org.openlmis.settings.service.ConfigurationSettingService;
import org.openlmis.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.Valid;

@Controller
@SuppressWarnings("PMD.TooManyMethods")
public class RequisitionController extends BaseController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequisitionController.class);

  @Autowired
  private RequisitionRepository requisitionRepository;

  @Autowired
  @Qualifier("beforeSaveRequisitionValidator")
  private RequisitionValidator validator;

  @Autowired
  private RequisitionService requisitionService;

  @Autowired
  private CommentRepository commentRepository;

  @Autowired
  private ConfigurationSettingService configurationSettingService;

  @InitBinder("requisition")
  protected void initBinder(final WebDataBinder binder) {
    binder.addValidators(validator);
  }

  /**
   * Allows creating new requisitions.
   *
   * @param requisition A requisition bound to the request body
   * @return ResponseEntity containing the created requisition
   */
  @RequestMapping(value = "/requisitions/initiate", method = POST)
  public ResponseEntity<?> initiateRequisition(@RequestBody @Valid Requisition requisition,
                                               BindingResult bindingResult) {
    try {
      Requisition newRequisition = requisitionService.initiateRequisition(requisition);
      return new ResponseEntity<>(newRequisition, HttpStatus.CREATED);
    } catch (RequisitionException ex) {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Submits earlier initiated requisition.
   */
  @RequestMapping(value = "/requisitions/{id}/submit", method = RequestMethod.PUT)
  public ResponseEntity<?> submitRequisition(@RequestBody @Valid Requisition requisition,
                                             BindingResult bindingResult,
                                             @PathVariable("id") UUID requisitionId) {
    if (!bindingResult.hasErrors()) {
      try {
        requisition = requisitionService.submitRequisition(requisition);
      } catch (RequisitionException ex) {
        LOGGER.debug(ex.getMessage(), ex);
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      }
      return new ResponseEntity<Object>(requisition, HttpStatus.OK);
    } else {
      return new ResponseEntity(getRequisitionErrors(bindingResult), HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Deletes requisition with the given id.
   */
  @RequestMapping(value = "/requisitions/{id}", method = RequestMethod.DELETE)
  public ResponseEntity<?> deleteRequisition(@PathVariable("id") UUID requisitionId) {
    try {
      boolean deleted = requisitionService.tryDelete(requisitionId);
      if (deleted) {
        return new ResponseEntity(HttpStatus.NO_CONTENT);
      } else {
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
      }
    } catch (RequisitionException ex) {
      LOGGER.debug(ex.getMessage(), ex);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Allows updating requisitions.
   *
   * @param requisition   A requisition bound to the request body
   * @param requisitionId UUID of requisition which we want to update
   * @return ResponseEntity containing the updated requisition
   */
  @RequestMapping(value = "/requisitions/{id}", method = RequestMethod.PUT)
  public ResponseEntity<?> updateRequisition(@RequestBody Requisition requisition,
                                             @PathVariable("id") UUID requisitionId) {
    try {
      LOGGER.debug("Updating requisition");
      Requisition requisitionToUpdate = requisitionRepository.save(requisition);
      return new ResponseEntity<Requisition>(requisitionToUpdate, HttpStatus.OK);
    } catch (DataIntegrityViolationException ex) {
      ErrorResponse errorResponse =
          new ErrorResponse("An error accurred while updating requisition", ex.getMessage());
      LOGGER.error(errorResponse.getMessage(), ex);
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Get all requisitions.
   *
   * @return Requisitions.
   */
  @RequestMapping(value = "/requisitions", method = RequestMethod.GET)
  @ResponseBody
  public ResponseEntity<?> getAllRequisitions() {
    Iterable<Requisition> requisitions = requisitionRepository.findAll();
    if (requisitions == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      return new ResponseEntity<>(requisitions, HttpStatus.OK);
    }
  }

  /**
   * Get chosen requisition.
   *
   * @param requisitionId UUID of requisition whose we want to get
   * @return Requisition.
   */
  @RequestMapping(value = "/requisitions/{id}", method = RequestMethod.GET)
  public ResponseEntity<?> getRequisition(@PathVariable("id") UUID requisitionId) {
    Requisition requisition = requisitionRepository.findOne(requisitionId);
    if (requisition == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      return new ResponseEntity<>(requisition, HttpStatus.OK);
    }
  }

  /**
   * Finds requisitions matching all of provided parameters.
   */
  @RequestMapping(value = "/requisitions/search", method = RequestMethod.GET)
  public ResponseEntity<?> searchRequisitions(
      @RequestParam(value = "facility", required = false) Facility facility,
      @RequestParam(value = "program", required = false) Program program,
      @RequestParam(value = "createdDateFrom", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdDateFrom,
      @RequestParam(value = "createdDateTo", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdDateTo,
      @RequestParam(value = "processingPeriod", required = false) ProcessingPeriod processingPeriod,
      @RequestParam(value = "supervisoryNode", required = false) SupervisoryNode supervisoryNode,
      @RequestParam(value = "requisitionStatus", required = false)
          RequisitionStatus requisitionStatus) {

    List<Requisition> result = requisitionService.searchRequisitions(facility, program,
        createdDateFrom, createdDateTo, processingPeriod, supervisoryNode, requisitionStatus);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  /**
   * Skipping chosen requisition period.
   */
  @RequestMapping(value = "/requisitions/{id}/skip", method = RequestMethod.PUT)
  public ResponseEntity<?> skipRequisition(@PathVariable("id") UUID requisitionId) {
    ResponseEntity<Object> responseEntity;
    try {
      Requisition requisition = requisitionService.skip(requisitionId);
      responseEntity = new ResponseEntity<>(requisition, HttpStatus.OK);
    } catch (RequisitionException ex) {
      LOGGER.debug(ex.getMessage(), ex);
      responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    return responseEntity;
  }

  /**
   * Rejecting requisition which is waiting for approve.
   */
  @RequestMapping(value = "/requisitions/{id}/reject", method = RequestMethod.PUT)
  public ResponseEntity<?> rejectRequisition(@PathVariable("id") UUID id) {
    Requisition rejectedRequisition = null;
    try {
      rejectedRequisition = requisitionService.reject(id);
    } catch (RequisitionException ex) {
      LOGGER.debug(ex.getMessage(), ex);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    return new ResponseEntity<>(rejectedRequisition, HttpStatus.OK);
  }

  /**
   * Add comment to the requisition.
   */
  @RequestMapping(value = "/requisitions/{id}/comments", method = RequestMethod.POST)
  public ResponseEntity<Object> insertComment(@RequestBody Comment comment, @PathVariable("id")
      UUID id, OAuth2Authentication auth) {
    Requisition requisition = requisitionRepository.findOne(id);
    comment.setRequisition(requisition);

    User user = (User) auth.getPrincipal();
    comment.setAuthor(user);
    commentRepository.save(comment);

    List<Comment> comments = requisition.getComments();
    MappingJacksonValue value = new MappingJacksonValue(comments);
    value.setSerializationView(View.BasicInformation.class);
    return new ResponseEntity<>(value, HttpStatus.OK);
  }

  /**
   * Get all comments for specified requisition.
   */
  @RequestMapping(value = "/requisitions/{id}/comments", method = RequestMethod.GET)
  public ResponseEntity<Object> getCommentsForRequisition(@PathVariable("id") UUID id) {
    Requisition requisition = requisitionRepository.findOne(id);
    List<Comment> comments = requisition.getComments();
    MappingJacksonValue value = new MappingJacksonValue(comments);
    value.setSerializationView(View.BasicInformation.class);
    return new ResponseEntity<>(value, HttpStatus.OK);
  }

  /**
   * Allows updating comments.
   *
   * @param comment   A comment bound to the request body
   * @param commentId UUID of comment which we want to update
   * @return ResponseEntity containing the updated requisition
   */
  @RequestMapping(value = "/requisitions/comments/{id}", method = RequestMethod.PUT)
  public ResponseEntity<?> updateRequisitionComment(@RequestBody Comment comment,
                                                    @PathVariable("id") UUID commentId,
                                                    OAuth2Authentication auth) {
    Comment requisitionComment = commentRepository.findOne(commentId);
    try {
      LOGGER.debug("Updating comment");
      comment.setRequisition(requisitionComment.getRequisition());

      User user = (User) auth.getPrincipal();
      comment.setAuthor(user);

      Comment updatedComment = commentRepository.save(comment);
      MappingJacksonValue value = new MappingJacksonValue(updatedComment);
      value.setSerializationView(View.BasicInformation.class);
      return new ResponseEntity<>(value, HttpStatus.OK);
    } catch (RestClientException ex) {
      ErrorResponse errorResponse =
          new ErrorResponse("An error accurred while updating comment", ex.getMessage());
      LOGGER.error(errorResponse.getMessage(), ex);
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Get chosen comment.
   *
   * @param commentId UUID of comment which we want to get
   * @return Comment.
   */
  @RequestMapping(value = "/requisitions/comments/{id}", method = RequestMethod.GET)
  public ResponseEntity<?> getChoosenRequisitionComment(@PathVariable("id") UUID commentId) {
    Comment comment = commentRepository.findOne(commentId);
    if (comment == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      MappingJacksonValue value = new MappingJacksonValue(comment);
      value.setSerializationView(View.BasicInformation.class);
      return new ResponseEntity<>(value, HttpStatus.OK);
    }
  }

  /**
   * Allows deleting requisitionLine.
   *
   * @param commentId UUID of requisitionLine which we want to delete
   * @return ResponseEntity containing the HTTP Status
   */
  @RequestMapping(value = "/requisitions/comments/{id}", method = RequestMethod.DELETE)
  public ResponseEntity<?> deleteRequisitionComment(@PathVariable("id") UUID commentId) {
    Comment comment = commentRepository.findOne(commentId);
    if (comment == null) {
      return new ResponseEntity(HttpStatus.NOT_FOUND);
    } else {
      try {
        commentRepository.delete(comment);
      } catch (DataIntegrityViolationException ex) {
        ErrorResponse errorResponse =
            new ErrorResponse("Comment cannot be deleted because of existing dependencies",
                ex.getMessage());
        LOGGER.error(errorResponse.getMessage(), ex);
        return new ResponseEntity(HttpStatus.CONFLICT);
      }
      return new ResponseEntity<Comment>(HttpStatus.NO_CONTENT);
    }
  }

  /**
   * Approve specified by id requisition.
   */
  @RequestMapping(value = "/requisitions/{id}/approve", method = RequestMethod.PUT)
  public ResponseEntity<?> approveRequisition(@PathVariable("id") UUID requisitionId) {
    Requisition requisition = requisitionRepository.findOne(requisitionId);
    if (requisition == null) {
      return new ResponseEntity(HttpStatus.NOT_FOUND);
    }
    if (requisition.getStatus() == RequisitionStatus.AUTHORIZED
        || (configurationSettingService.getBoolValue("skipAuthorization")
        && requisition.getStatus() == RequisitionStatus.SUBMITTED)) {
      requisition.setStatus(RequisitionStatus.APPROVED);
      requisitionRepository.save(requisition);
      LOGGER.debug("Requisition with id " + requisitionId + " approved");
      return new ResponseEntity<>(requisition, HttpStatus.OK);
    } else {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Get requisitions to approve for right supervisor.
   */
  @RequestMapping(value = "/requisitions/requisitions-for-approval", method = RequestMethod.GET)
  public ResponseEntity<Object> listForApproval(OAuth2Authentication auth) {
    User user = (User) auth.getPrincipal();
    List<Requisition> requisitions = requisitionService.getRequisitionsForApproval(user.getId());
    return new ResponseEntity<>(requisitions, HttpStatus.OK);
  }

  private Map<String, String> getRequisitionErrors(BindingResult bindingResult) {
    return new HashMap<String, String>() {
      {
        for (FieldError error : bindingResult.getFieldErrors()) {
          put(error.getField(), error.getCode());
        }
      }
    };
  }

  /**
   * Get all submitted Requisitions.
   *
   * @return Submitted requisitions.
   */
  @RequestMapping(value = "/requisitions/submitted", method = RequestMethod.GET)
  @ResponseBody
  public ResponseEntity<?> getSubmittedRequisitions() {

    Iterable<Requisition> submittedRequisitions = requisitionService.searchRequisitions(
        null, null, null, null, null, null, RequisitionStatus.SUBMITTED);
    if (submittedRequisitions == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      return new ResponseEntity<>(submittedRequisitions, HttpStatus.OK);
    }
  }

  /**
   * Authorize given requisition.
   *
   * @param requisitionDto Requisition object to be authorized.
   * @param bindingResult  Object used for validation.
   * @param requisitionId  UUID of Requisition to authorize.
   * @return ResponseEntity with authorized Requisition if authorization was successful.
   */
  @RequestMapping(value = "/requisitions/{id}/authorize", method = RequestMethod.PUT)
  public ResponseEntity<?> authorizeRequisition(@RequestBody Requisition requisitionDto,
                                                BindingResult bindingResult,
                                                @PathVariable("id") UUID requisitionId) {

    if (requisitionId == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    try {
      requisitionDto = requisitionService.authorize(requisitionId, requisitionDto,
          bindingResult.hasErrors());
      LOGGER.info("Requisition: " + requisitionId + " authorized.");
    } catch (RequisitionException ex) {
      LOGGER.debug(ex.getMessage(), ex);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    return new ResponseEntity<>(requisitionDto, HttpStatus.OK);
  }

  /**
   * Get approved requisitions matching all of provided parameters.
   *
   * @param filterValue Value to be used to filter.
   * @param filterBy Field used to filter: "programName", "facilityCode", "facilityName" or "all".
   * @param sortBy Field used to sort: "programName", "facilityCode" or "facilityName".
   * @param descending Descending direction for sort.
   * @param pageNumber Page number to return.
   * @param pageSize Quantity for one page.
   *
   * @return ResponseEntity with list of approved requisitions.
   */
  @RequestMapping(value = "/requisitions/approved/search", method = RequestMethod.GET)
  public ResponseEntity<?> searchApprovedRequisitionsWithSortAndFilterAndPaging(
      @RequestParam String filterValue,
      @RequestParam String filterBy,
      @RequestParam String sortBy,
      @RequestParam Boolean descending,
      @RequestParam Integer pageNumber,
      @RequestParam Integer pageSize) {

    // TODO Add filtering about available Requisition for user
    // (If Reference Data Service - EBAC will be finished)
    // TODO Add available supplying depot and filtering about this
    // (If OLMIS-227 will be finished)

    List<Requisition> approvedRequisitionList =
        requisitionService.searchApprovedRequisitionsWithSortAndFilterAndPaging(
            filterValue, filterBy, sortBy, descending, pageNumber, pageSize);

    return new ResponseEntity<>(approvedRequisitionList, HttpStatus.OK);
  }

}
