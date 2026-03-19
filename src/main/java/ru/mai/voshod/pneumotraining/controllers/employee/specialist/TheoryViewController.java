package ru.mai.voshod.pneumotraining.controllers.employee.specialist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.dto.TheorySectionDTO;
import ru.mai.voshod.pneumotraining.service.employee.chief.TheoryMaterialService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TheorySectionService;

import java.util.Optional;

@Controller
@Slf4j
public class TheoryViewController {

    private final TheorySectionService theorySectionService;
    private final TheoryMaterialService theoryMaterialService;

    public TheoryViewController(TheorySectionService theorySectionService,
                                TheoryMaterialService theoryMaterialService) {
        this.theorySectionService = theorySectionService;
        this.theoryMaterialService = theoryMaterialService;
    }

    @GetMapping("/employee/specialist/theory/allSections")
    public String allSections(Model model) {
        model.addAttribute("allSections", theorySectionService.getAllSections());
        return "employee/specialist/theory/allSections";
    }

    @GetMapping("/employee/specialist/theory/viewSection/{id}")
    public String viewSection(@PathVariable(value = "id") long id, Model model) {
        Optional<TheorySectionDTO> sectionOptional = theorySectionService.getSectionById(id);
        if (sectionOptional.isEmpty()) {
            return "redirect:/employee/specialist/theory/allSections";
        }
        model.addAttribute("sectionDTO", sectionOptional.get());
        model.addAttribute("allMaterials", theoryMaterialService.getMaterialsBySection(id));
        return "employee/specialist/theory/viewSection";
    }

    @GetMapping("/employee/specialist/theory/viewMaterial/{id}")
    public String viewMaterial(@PathVariable(value = "id") long id, Model model) {
        Optional<TheoryMaterialDTO> materialOptional = theoryMaterialService.getMaterialById(id);
        if (materialOptional.isEmpty()) {
            return "redirect:/employee/specialist/theory/allSections";
        }
        model.addAttribute("materialDTO", materialOptional.get());
        return "employee/specialist/theory/viewMaterial";
    }
}
