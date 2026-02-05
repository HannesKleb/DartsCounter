package com.DartsCounter.DartsCounter_backend;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
public class ScoreCommunication {
    
    public String coordsToField(BigDecimal radius, BigDecimal angle){
        
        if (radius < 0.0282){ // check inner Bull
            return "IB";
        }
        
        return "String";
    }

}
