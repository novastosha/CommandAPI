package net.zoda.api.command.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@RequiredArgsConstructor
public class Pair<A,B> {

    @Getter private final A a;
    @Getter private final B b;

}
