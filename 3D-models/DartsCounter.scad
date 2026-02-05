$fn=96;
$print_wall_thickness = 0.4;

module middle_part(diameter, height, hole_distance, hole_diameter, arm_width, arm_holeDepth, arm_skrew){
    
        difference(){
            union(){
                cylinder(d=diameter, h=height, center=true);
                
                //arm straighters
                translate([0,diameter/2-arm_holeDepth/2,0])
                cube([arm_width+1, arm_holeDepth, height], center=true);
        
                rotate([0,0,120])
                translate([0,diameter/2-arm_holeDepth/2,0])
                cube([arm_width+1, arm_holeDepth, height], center=true);
        
                rotate([0,0,-120])
                translate([0,diameter/2-arm_holeDepth/2,0])
                cube([arm_width+1, arm_holeDepth, height], center=true);
                
            }
            
            //middle holes
            translate([hole_distance/2,0,0])
            cylinder(h=height+1, d=hole_diameter, center=true);
            
            translate([-hole_distance/2,0,0])
            cylinder(h=height+1, d=hole_diameter, center=true);
            
            //arm holes
            translate([0,diameter/2-arm_holeDepth/2,height/2])
            cube([arm_width, arm_holeDepth, height], center=true);
        
            rotate([0,0,120])
            translate([0,diameter/2-arm_holeDepth/2,height/2])
            cube([arm_width, arm_holeDepth, height], center=true);
        
            rotate([0,0,-120])
            translate([0,diameter/2-arm_holeDepth/2,height/2])
            cube([arm_width, arm_holeDepth, height], center=true);
            
            //arm skrew holes
            
            rotate([0,0,0]){
                translate([arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            
                translate([-arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            }
            rotate([0,0,120]){
                translate([arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            
                translate([-arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            }
            rotate([0,0,-120]){
                translate([arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            
                translate([-arm_width/6,diameter/2-arm_holeDepth/2,0])
                cylinder(h=height+1, d=arm_skrew, center=true);
            }
            
        }
    };




module arm(width, height, length, hole_depth, skrew_size){
    difference(){    
        union(){
            //main body
            cube([width, length-2*hole_depth, height], center=true);
            
            translate([0,(length-hole_depth)/2,-height*0.25]){
                difference(){
                    cube([width, hole_depth, height/2], center=true);
                    translate([width/6,0,0])
                    cylinder(h=height+1, d=skrew_size, center=true);
                    translate([-width/6,0,0])
                    cylinder(h=height+1, d=skrew_size, center=true);
                }
            }
            
            translate([0,-(length-hole_depth)/2,-height*0.25]){
                difference(){
                    cube([width, hole_depth, height/2], center=true);
                    translate([width/6,0,0])
                    cylinder(h=height+1, d=skrew_size, center=true);
                    translate([-width/6,0,0])
                    cylinder(h=height+1, d=skrew_size, center=true);
                }
            }
        }
    }
}






module camShell(length, width, height, lens_diameter, knob_diameter, wall, skrew_size, cable_slot_height){
    
    translate([0,length,0])
    difference(){
        //shell
        union(){
            translate([0,0,-wall/2])
            cube([length-height, height+2*wall, width/2+wall], center = true);
            
            translate([(length-height)/2,0,-wall/2])
            cylinder(h=width/2+wall, d=height+2*wall, center=true);
            
            translate([-(length-height)/2,0,-wall/2])
            cylinder(h=width/2+wall, d=height+2*wall, center=true);
            
            //skrew holes
            translate([(length)/2+(skrew_size+6.9)/2,0,width/4-2.5]){
                difference(){
                    union(){
                        cylinder(h=5, d=skrew_size+6.9, center=true);
                    
                        translate([-(skrew_size+6.9)/2,0,0])
                        cube([skrew_size+6.9, skrew_size+6.9, 5], center=true);
                    }
                    cylinder(h=5, d=skrew_size, center=true);
                }
            }
            rotate([0,0,180])
            translate([(length)/2+(skrew_size+6.9)/2,0,width/4-2.5]){
                difference(){
                    union(){
                        cylinder(h=5, d=skrew_size+6.9, center=true);
                    
                        translate([-(skrew_size+6.9)/2,0,0])
                        cube([skrew_size+6.9, skrew_size+6.9, 5], center=true);
                    }
                    cylinder(h=5, d=skrew_size, center=true);
                }
            }
            
            
        }
            
        //cam Base
        cube([length-height, height, width/2], center = true);
            
        translate([(length-height)/2,0,0])
        cylinder(h=width/2, d=height, center=true);
            
        translate([-(length-height)/2,0,0])
        cylinder(h=width/2, d=height, center=true);
        
        //knob hole
        translate([0,height/2+wall/2,0])
        cube([knob_diameter,wall, height/2], center = true);
        
        //lens hole
        translate([0,0,-(width/2)+2*wall])
        cylinder(h=wall*2, d=lens_diameter, center = true);
        
    }
    
    
    translate([0,-length,0])
    difference(){
        //shell
        union(){
            translate([0,0,-wall/2])
            cube([length-height, height+2*wall, width/2+wall], center = true);
            
            translate([(length-height)/2,0,-wall/2])
            cylinder(h=width/2+wall, d=height+2*wall, center=true);
            
            translate([-(length-height)/2,0,-wall/2])
            cylinder(h=width/2+wall, d=height+2*wall, center=true);
            
            //connector
            translate([0,-height/2-wall-5,-width/4-wall+2.5])
            difference(){
                cube([30,10,5], center=true);
                translate([30/6,0,0])
                cylinder(h=5, d=skrew_size, center=true);
                translate([-30/6,0,0])
                cylinder(h=5, d=skrew_size, center=true);
            }
            
            //skrew holes
            translate([(length)/2+(skrew_size+6.9)/2,0,width/4-2.5]){
                difference(){
                    union(){
                        cylinder(h=5, d=skrew_size+6.9, center=true);
                    
                        translate([-(skrew_size+6.9)/2,0,0])
                        cube([skrew_size+6.9, skrew_size+6.9, 5], center=true);
                    }
                    cylinder(h=5, d=skrew_size, center=true);
                }
            }
            rotate([0,0,180])
            translate([(length)/2+(skrew_size+6.9)/2,0,width/4-2.5]){
                difference(){
                    union(){
                        cylinder(h=5, d=skrew_size+6.9, center=true);
                    
                        translate([-(skrew_size+6.9)/2,0,0])
                        cube([skrew_size+6.9, skrew_size+6.9, 5], center=true);
                    }
                    cylinder(h=5, d=skrew_size, center=true);
                }
            }
            
            
        }
            
        //cam Base
        cube([length-height, height, width/2], center = true);
            
        translate([(length-height)/2,0,0])
        cylinder(h=width/2, d=height, center=true);
            
        translate([-(length-height)/2,0,0])
        cylinder(h=width/2, d=height, center=true);
        
        //knob hole
        translate([0,height/2+wall/2,0])
        cube([knob_diameter,wall, height/2], center = true);
        
        //cable Slot
        translate([0,width/2-4,-(width/2)+2*wall])
        cube([length-height/2,cable_slot_height,wall*2], center = true);
        
    }
}



module angel(board_wall_distance, connection_lens_distance, board_depth, skrew_size, wall){
    length= board_wall_distance+board_depth-connection_lens_distance;
    cube([length-10, 30, 10], center=true);
    
    translate([length/2,0,-2.5]){
        difference(){
            cube([10,30,5], center=true);
            translate([0,30/6,0])
            cylinder(h=5, d=skrew_size, center=true);
            translate([0,-30/6,0])
            cylinder(h=5, d=skrew_size, center=true);
        }
    }
    
    rotate([0,90,0])
    translate([-10,0,-length/2+7.5+5]){
        difference(){
            cube([10,30,5], center=true);
            translate([0,30/6,0])
            cylinder(h=5, d=skrew_size, center=true);
            translate([0,-30/6,0])
            cylinder(h=5, d=skrew_size, center=true);
        }
    }
}


module rpi_arm(arm_width, arm_height, arm_length, arm_hole_depth, skrew_size, wall, length, width, height){
    difference(){
        union(){
            rotate([0,180,0])
            arm(
                width = arm_width,
                height = arm_height,
                length = arm_length,
                hole_depth = arm_hole_depth,
                skrew_size = skrew_size
            );
            
            translate([0,0,-arm_height/2+2.5])
            cube([length+2*wall, width+2*wall, 5], center=true);
        }
        
        //inner
        translate([0,0,height/2-3])
        cube([length, width, height], center=true);
        
        //arm place maker
        translate([0,0,height/2])
        cube([length, width+2*wall+20+1, height], center=true);
        
        //skrews
        translate([0,width/2+wall+5,-2.5]){
            translate([30/6,0,0])
            #cylinder(h=5, d=skrew_size, center=true);
            translate([-30/6,0,0])
            cylinder(h=5, d=skrew_size, center=true);
        }
        
        rotate([0,0,180])
        translate([0,width/2+wall+5,-2.5]){
            translate([30/6,0,0])
            cylinder(h=5, d=skrew_size, center=true);
            translate([-30/6,0,0])
            cylinder(h=5, d=skrew_size, center=true);
        }
        
    }
}

/*
middle_part(
    diameter = 60,
    height = 10,
    hole_distance = 30,
    hole_diameter = 5,
    arm_width = 30,
    arm_holeDepth = 10,
    arm_skrew = 3.1
);
*/

/*
arm(
    width = 30,
    height = 10,
    length = 195,
    hole_depth = 10,
    skrew_size = 3.1
);
*/

/*
camShell(
    length = 70.5,
    width = 20.5,
    height = 26.5,
    lens_diameter = 18.5,
    knob_diameter = 14,
    wall = 2,
    skrew_size = 3.1,
    cable_slot_height = 8
);
*/

/*
angel(
    board_wall_distance = 25, //distance betweeen the board and the wall norm: 25mm
    connection_lens_distance = 14, // distance between the middle of the lens and the connector norm: 12mm (lens to end of cam) + 2 (wall) = 14mm
    board_depth = 40,
    skrew_size = 3.1
);
*/

rpi_arm(
    arm_width = 30,
    arm_height = 10,
    arm_length = 195,
    arm_hole_depth = 10,
    skrew_size = 3.1,
    wall = 2,
    length = 50,
    width = 40,
    height = 21
);