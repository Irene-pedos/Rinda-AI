package com.rinda.backend.user;

public record CooperativeResponse(long id, String name, String district, String sector, boolean isDemo) {

    public static CooperativeResponse of(Cooperative c) {
        return new CooperativeResponse(c.getId(), c.getName(), c.getDistrict(), c.getSector(), c.isDemo());
    }
}
