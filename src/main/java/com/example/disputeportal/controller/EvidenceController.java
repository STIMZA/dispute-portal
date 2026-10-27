package com.example.disputeportal.controller;

import com.example.disputeportal.dto.EvidenceFile;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.service.EvidenceService;
import java.io.IOException;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The customer's side of evidence: upload a file to one of their own disputes, and download one back. The rules
 * (ownership, open dispute, size, type, limits) are all in {@link EvidenceService}; this class only translates
 * between HTTP and the service.
 */
@Controller
public class EvidenceController {

    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    /**
     * Receives the upload form. Success and failure both redirect back to the dispute page with a message
     * (Post/Redirect/Get), so refreshing the page never uploads the file again.
     */
    @PostMapping("/disputes/{id}/evidence")
    public String upload(@PathVariable Long id, @RequestParam(value = "file", required = false) MultipartFile file,
                         Principal principal, RedirectAttributes redirect) {
        if (file == null || file.isEmpty()) {
            redirect.addFlashAttribute("errorMessage", "Choose a file to upload.");
            return "redirect:/disputes/" + id;
        }
        try {
            evidenceService.upload(id, principal.getName(), file.getOriginalFilename(), file.getBytes());
            redirect.addFlashAttribute("successMessage", "Evidence uploaded. The dispute team has been told.");
        } catch (BusinessRuleException ex) {
            // A rule was broken (wrong type, too big, dispute closed ...): show its message on the dispute page.
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (IOException ex) {
            log.warn("Could not read an uploaded file for dispute {}", id, ex);
            redirect.addFlashAttribute("errorMessage", "We couldn't read that file. Please try again.");
        }
        return "redirect:/disputes/" + id;
    }

    /** Downloads one file of the customer's own dispute. Someone else's dispute or file id gives a 404. */
    @GetMapping("/disputes/{id}/evidence/{evidenceId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long evidenceId,
                                             Principal principal) {
        EvidenceFile file = evidenceService.downloadForCustomer(id, evidenceId, principal.getName());
        return EvidenceDownloads.attachment(file);
    }
}
