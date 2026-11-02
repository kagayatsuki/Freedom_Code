package com.coding.agent.service;

import com.coding.agent.domain.dto.LoginUserDTO;
import com.coding.agent.domain.dto.RegisterDTO;
import com.coding.agent.domain.po.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.coding.agent.domain.vo.LoginVO;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author kagayatsuki
 * @since 2026-05-22
 */
public interface ITUserService extends IService<User> {

    LoginVO login(LoginUserDTO loginUserDTO);

    void register(RegisterDTO registerUserDTO);
}
