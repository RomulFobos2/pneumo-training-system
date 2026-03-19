package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.dto.TheorySectionDTO;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;
import ru.mai.voshod.pneumotraining.service.employee.chief.TheoryMaterialService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TheorySectionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class TheorySectionController {

    private final TheorySectionService theorySectionService;
    private final TheoryMaterialService theoryMaterialService;

    public TheorySectionController(TheorySectionService theorySectionService,
                                   TheoryMaterialService theoryMaterialService) {
        this.theorySectionService = theorySectionService;
        this.theoryMaterialService = theoryMaterialService;
    }

    // ========== AJAX проверка уникальности названия раздела ==========

    @GetMapping("/employee/chief/materials/check-title")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkTitle(@RequestParam String title,
                                                            @RequestParam(required = false) Long id) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", theorySectionService.checkTitle(title, id));
        return ResponseEntity.ok(response);
    }

    // ========== Разделы ==========

    @GetMapping("/employee/chief/materials/allSections")
    public String allSections(Model model) {
        model.addAttribute("allSections", theorySectionService.getAllSections());
        return "employee/chief/materials/allSections";
    }

    @GetMapping("/employee/chief/materials/addSection")
    public String addSectionForm() {
        return "employee/chief/materials/addSection";
    }

    @PostMapping("/employee/chief/materials/addSection")
    public String addSection(@RequestParam String inputTitle,
                             @RequestParam Integer inputSortOrder,
                             @RequestParam(required = false) String inputDescription,
                             Model model) {
        Optional<Long> result = theorySectionService.saveSection(inputTitle, inputSortOrder, inputDescription);
        if (result.isEmpty()) {
            model.addAttribute("sectionError", "Ошибка при сохранении. Возможно, название уже занято.");
            return "employee/chief/materials/addSection";
        }
        return "redirect:/employee/chief/materials/detailsSection/" + result.get();
    }

    @GetMapping("/employee/chief/materials/detailsSection/{id}")
    public String detailsSection(@PathVariable(value = "id") long id, Model model) {
        Optional<TheorySectionDTO> sectionOptional = theorySectionService.getSectionById(id);
        if (sectionOptional.isEmpty()) {
            return "redirect:/employee/chief/materials/allSections";
        }
        model.addAttribute("sectionDTO", sectionOptional.get());
        model.addAttribute("allMaterials", theoryMaterialService.getMaterialsBySection(id));
        return "employee/chief/materials/detailsSection";
    }

    @GetMapping("/employee/chief/materials/editSection/{id}")
    public String editSectionForm(@PathVariable(value = "id") long id, Model model) {
        Optional<TheorySectionDTO> sectionOptional = theorySectionService.getSectionById(id);
        if (sectionOptional.isEmpty()) {
            return "redirect:/employee/chief/materials/allSections";
        }
        model.addAttribute("sectionDTO", sectionOptional.get());
        return "employee/chief/materials/editSection";
    }

    @PostMapping("/employee/chief/materials/editSection/{id}")
    public String editSection(@PathVariable(value = "id") long id,
                              @RequestParam String inputTitle,
                              @RequestParam Integer inputSortOrder,
                              @RequestParam(required = false) String inputDescription,
                              RedirectAttributes redirectAttributes) {
        Optional<Long> result = theorySectionService.editSection(id, inputTitle, inputSortOrder, inputDescription);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("sectionError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/chief/materials/editSection/" + id;
        }
        return "redirect:/employee/chief/materials/detailsSection/" + id;
    }

    @GetMapping("/employee/chief/materials/deleteSection/{id}")
    public String deleteSection(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        if (theorySectionService.deleteSection(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Раздел удалён.");
            return "redirect:/employee/chief/materials/allSections";
        }
        redirectAttributes.addFlashAttribute("errorMessage",
                "Невозможно удалить раздел. Сначала удалите все материалы из раздела.");
        return "redirect:/employee/chief/materials/detailsSection/" + id;
    }

    // ========== Материалы ==========

    @GetMapping("/employee/chief/materials/addMaterial/{sectionId}")
    public String addMaterialForm(@PathVariable(value = "sectionId") long sectionId, Model model) {
        Optional<TheorySectionDTO> sectionOptional = theorySectionService.getSectionById(sectionId);
        if (sectionOptional.isEmpty()) {
            return "redirect:/employee/chief/materials/allSections";
        }
        model.addAttribute("sectionDTO", sectionOptional.get());
        model.addAttribute("allMaterialTypes", MaterialType.values());
        return "employee/chief/materials/addMaterial";
    }

    @PostMapping("/employee/chief/materials/addMaterial/{sectionId}")
    public String addMaterial(@PathVariable(value = "sectionId") long sectionId,
                              @RequestParam String inputTitle,
                              @RequestParam(required = false) String inputContent,
                              @RequestParam Integer inputSortOrder,
                              @RequestParam String inputMaterialType,
                              @RequestParam(required = false) MultipartFile inputFile,
                              Model model) {
        Optional<Long> result;
        if ("PDF".equals(inputMaterialType) && inputFile != null && !inputFile.isEmpty()) {
            result = theoryMaterialService.saveMaterialWithFile(sectionId, inputTitle, inputFile, inputSortOrder);
        } else {
            result = theoryMaterialService.saveMaterial(sectionId, inputTitle, inputContent, inputSortOrder, inputMaterialType);
        }
        if (result.isEmpty()) {
            model.addAttribute("materialError", "Ошибка при сохранении материала.");
            model.addAttribute("sectionDTO", theorySectionService.getSectionById(sectionId).orElse(null));
            model.addAttribute("allMaterialTypes", MaterialType.values());
            return "employee/chief/materials/addMaterial";
        }
        return "redirect:/employee/chief/materials/detailsSection/" + sectionId;
    }

    @GetMapping("/employee/chief/materials/editMaterial/{id}")
    public String editMaterialForm(@PathVariable(value = "id") long id, Model model) {
        Optional<TheoryMaterialDTO> materialOptional = theoryMaterialService.getMaterialById(id);
        if (materialOptional.isEmpty()) {
            return "redirect:/employee/chief/materials/allSections";
        }
        model.addAttribute("materialDTO", materialOptional.get());
        model.addAttribute("allMaterialTypes", MaterialType.values());
        return "employee/chief/materials/editMaterial";
    }

    @PostMapping("/employee/chief/materials/editMaterial/{id}")
    public String editMaterial(@PathVariable(value = "id") long id,
                               @RequestParam String inputTitle,
                               @RequestParam(required = false) String inputContent,
                               @RequestParam Integer inputSortOrder,
                               @RequestParam String inputMaterialType,
                               @RequestParam(required = false) MultipartFile inputFile,
                               RedirectAttributes redirectAttributes) {
        Optional<Long> sectionIdOpt = theoryMaterialService.getSectionIdByMaterialId(id);
        Optional<Long> result;
        if ("PDF".equals(inputMaterialType) && inputFile != null && !inputFile.isEmpty()) {
            result = theoryMaterialService.editMaterialWithFile(id, inputTitle, inputFile, inputSortOrder);
        } else {
            result = theoryMaterialService.editMaterial(id, inputTitle, inputContent, inputSortOrder, inputMaterialType);
        }
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("materialError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/chief/materials/editMaterial/" + id;
        }
        return "redirect:/employee/chief/materials/detailsSection/" + sectionIdOpt.orElse(0L);
    }

    @GetMapping("/employee/chief/materials/deleteMaterial/{id}")
    public String deleteMaterial(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        Optional<Long> sectionIdOpt = theoryMaterialService.getSectionIdByMaterialId(id);
        long sectionId = sectionIdOpt.orElse(0L);
        if (theoryMaterialService.deleteMaterial(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Материал удалён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении материала.");
        }
        return "redirect:/employee/chief/materials/detailsSection/" + sectionId;
    }

    // ========== Перестановка (AJAX) ==========

    @PostMapping("/employee/chief/materials/reorderSections")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> reorderSections(@RequestBody List<Long> orderedIds) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", theorySectionService.reorderSections(orderedIds));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee/chief/materials/reorderMaterials/{sectionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> reorderMaterials(
            @PathVariable(value = "sectionId") long sectionId,
            @RequestBody List<Long> orderedIds) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", theoryMaterialService.reorderMaterials(sectionId, orderedIds));
        return ResponseEntity.ok(response);
    }
}
