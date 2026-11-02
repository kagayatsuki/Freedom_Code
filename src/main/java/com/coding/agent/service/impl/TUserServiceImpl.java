package com.coding.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.agent.domain.dto.LoginUserDTO;
import com.coding.agent.domain.dto.RegisterDTO;
import com.coding.agent.domain.po.User;
import com.coding.agent.domain.vo.LoginVO;
import com.coding.agent.exception.BadRequestException;
import com.coding.agent.exception.BaseException;
import com.coding.agent.mapper.TUserMapper;
import com.coding.agent.service.ITUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.agent.utils.JwtUtils;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author kagayatsuki
 * @since 2026-05-22
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TUserServiceImpl extends ServiceImpl<TUserMapper, User> implements ITUserService {
    private final TUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    @Override
    public LoginVO login(LoginUserDTO loginUserDTO) {
        User user=userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, loginUserDTO.getUsername())
        );
        if(user==null){
            throw new BadRequestException("用户不存在！！");
        }
        if(!passwordEncoder.matches(loginUserDTO.getPassword(),user.getPassword())){
            throw new BadRequestException("用户名或密码错误");
        }
        if(user.getStatus() != null && user.getStatus() == 0){
            throw new BadRequestException("账号已被禁用，请联系管理员");
        }

        // 更新最后登录时间
        user.setLastLogin(java.time.LocalDateTime.now());
        userMapper.updateById(user);

        String token= JwtUtils.generateToken(user.getId(),user.getUsername());
        return new LoginVO(token, user.getPassword(), user.getStatus(),
                user.getEmail(), user.getImage());
    }

    @Override
    public void register(RegisterDTO registerUserDTO) {
        User newUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, registerUserDTO.getUsername()));

        if(newUser!=null){
            throw new BadRequestException("该用户已存在");
        }
        User u2=userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, registerUserDTO.getEmail()));
        if(u2!=null){
            throw new BadRequestException("该邮箱已被使用");
        }
        User user=new User();
        user.setUsername(registerUserDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerUserDTO.getPassword()));
        user.setStatus(1);
        user.setEmail(registerUserDTO.getEmail());
        userMapper.insert(user);
    }
}
