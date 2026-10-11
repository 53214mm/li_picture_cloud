package com.li.lipicturecloud.controller;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.companion.observation.*;
import com.li.lipicturecloud.common.*;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping(value="/companion/observation",produces="application/json")
@ConditionalOnProperty(prefix="app.companion",name="enabled",havingValue="true",matchIfMissing=true)
public class CompanionObservationController {
 private final CompanionObservationService service;private final UserService users;
 public CompanionObservationController(CompanionObservationService service,UserService users){this.service=service;this.users=users;}
 @AuthCheck @GetMapping("/status") public BaseResponse<?> status(HttpServletRequest request){return ResultUtils.success(service.status(owner(request)));}
 @AuthCheck @PostMapping("/session") public BaseResponse<?> open(@RequestBody ObservationContracts.OpenInput body,HttpServletRequest request){return ResultUtils.success(service.open(owner(request),body));}
 @AuthCheck @PostMapping("/prepare") public BaseResponse<?> prepare(@RequestBody CompanionObservationService.PrepareInput body,HttpServletRequest request){return ResultUtils.success(service.prepare(owner(request),body));}
 @AuthCheck @PostMapping("/preview") public BaseResponse<?> preview(@RequestBody ObservationContracts.PageInput body,HttpServletRequest request){return ResultUtils.success(service.preview(owner(request),body));}
 @AuthCheck @PostMapping("/observe") public BaseResponse<?> observe(@RequestBody CompanionObservationService.ObserveInput body,HttpServletRequest request){return ResultUtils.success(service.observe(owner(request),body));}
 @AuthCheck @DeleteMapping("/session/{id}") public BaseResponse<?> close(@PathVariable String id,HttpServletRequest request){return ResultUtils.success(service.close(owner(request),id));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public org.springframework.http.ResponseEntity<BaseResponse<?>> invalid(Exception error){
  // No echo of arbitrary request fields, Jackson diagnostics, or credential-bearing exceptions.
  String reason=error instanceof IllegalArgumentException && error.getMessage()!=null && error.getMessage().matches("[A-Z_]{1,64}")?error.getMessage():"OBSERVATION_REQUEST_INVALID";
  return org.springframework.http.ResponseEntity.badRequest().body(ResultUtils.error(40000,reason));
 }
 private long owner(HttpServletRequest request){return users.getLoginUserEntity(request).getId();}
}
