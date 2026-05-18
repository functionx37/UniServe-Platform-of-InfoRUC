package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.PartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/party")
public class PartyController {

    @Autowired
    private PartyService partyService;

    @GetMapping("/progress")
    public Result<PartyService.PartyProgressVO> progress() {
        try {
            return Result.success(partyService.getProgress());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}