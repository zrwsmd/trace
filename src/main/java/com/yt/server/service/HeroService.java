package com.yt.server.service;

import com.yt.server.entity.Hero;

public interface HeroService {

    Hero selectByPrimaryKey(Integer id);
}
