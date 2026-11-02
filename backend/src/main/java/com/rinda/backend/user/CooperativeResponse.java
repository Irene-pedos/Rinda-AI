package com.rinda.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record CooperativeResponse(long id, String name, String district,
                                  @Schema(types = {"string", "null"}) String sector, boolean isDemo) {

    public static CooperativeResponse of(Cooperative c) {
        return new CooperativeResponse(c.getId(), c.getName(), c.getDistrict(), c.getSector(), c.isDemo());
    }
}
