package ru.mai.voshod.pneumotraining.mapper;

import ru.mai.voshod.pneumotraining.dto.RoleDTO;
import ru.mai.voshod.pneumotraining.models.Role;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RoleMapper {
    RoleMapper INSTANCE = Mappers.getMapper(RoleMapper.class);

    RoleDTO toDTO(Role role);

    List<RoleDTO> toDTOList(List<Role> roles);
}
