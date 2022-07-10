package net.zoda.api.command.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@RequiredArgsConstructor
public class TriPair<A,B,C> {

    @Getter private final A a;
    @Getter private final B b;
    @Getter private final C c;
}
