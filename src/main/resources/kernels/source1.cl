         const sampler_t sampler =   CLK_NORMALIZED_COORDS_FALSE |
                                     CLK_ADDRESS_CLAMP_TO_EDGE |
                                     CLK_FILTER_NEAREST;

		__kernel void rotateImage(
		    __read_only  image2d_t source,
		    __write_only image2d_t target,
            float angle)
		{
		    int x = get_global_id(0);
		    int y = get_global_id(1);
            uint4 pixel = read_imageui(source, sampler, (int2)(x, y));

            float Y  = 16 + (65.481 * pixel.x + 128.553 * pixel.y + 24.966 * pixel.z);
            float Cb = 128 + (-37.797 * pixel.x - 74.203 * pixel.y + 112.0 * pixel.z);
            float Cr = 128 + (112.0 * pixel.x - 93.786 * pixel.y - 18.214 * pixel.z);
            float a = pixel.w;

            write_imageui(target, (int2)(x,y), (uint4)(Y, Cb, Cr, 0));
		}