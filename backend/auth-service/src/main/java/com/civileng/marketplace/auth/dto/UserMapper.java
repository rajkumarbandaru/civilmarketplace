package com.civileng.marketplace.auth.dto;

import com.civileng.marketplace.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "role", source = "role.name")
    AuthResponse.UserDto toUserDto(User user);
}
