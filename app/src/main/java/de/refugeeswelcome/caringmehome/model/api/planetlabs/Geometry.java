package de.refugeeswelcome.caringmehome.model.api.planetlabs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by root1 on 04/06/16.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Geometry {

    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
