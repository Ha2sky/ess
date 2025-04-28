package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PatternController {
    @GetMapping("/admin/pattern")
    public String patternPage() {
        return "pattern";
    }
}
