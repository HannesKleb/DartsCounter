package com.DartsCounter.DartsCounter_backend;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
public class ScoreCommunication {
    
    private static final BigDecimal INNER_BULL_RADIUS = new BigDecimal("0.0282");

    public String coordsToField(BigDecimal radius, BigDecimal angle){
        
        if (radius.compareTo(INNER_BULL_RADIUS) < 0){ // check inner Bull
            return "IB";
        }
        
        return "String";
    }

}
