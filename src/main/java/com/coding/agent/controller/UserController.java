package com.coding.agent.controller;


import com.coding.agent.domain.dto.LoginUserDTO;
import com.coding.agent.domain.dto.RegisterDTO;
import com.coding.agent.domain.vo.LoginVO;
import com.coding.agent.service.ITUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final ITUserService userService;

    /**
     * 用户登录接口
     * 💡 成功：直接返回 LoginVO，Spring MVC 自动将其序列化为纯 JSON
     * 💡 失败：内部抛出 BizException 异常，被全局异常处理器捕获
     */
    @PostMapping("/login")
    public LoginVO login(@RequestBody LoginUserDTO loginUserDTO) {
        return userService.login(loginUserDTO);
    }
    @PostMapping("/register")
   // 💡 逼格拉满：成功直接返回 HTTP 201 Created 状态码
    public void register(@RequestBody RegisterDTO registerUserDTO) {
        userService.register(registerUserDTO);
    }
}