package de.jos;

import org.jocl.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.jocl.CL.*;


public class App {
  private cl_context context;
  private cl_command_queue commandQueue;

  BufferedImage in1;
  BufferedImage in2;
  BufferedImage in3;
  BufferedImage in4;

  BufferedImage out1;
  BufferedImage out2;
  BufferedImage out3;
  BufferedImage out4;

  public static void main(String[] args) throws Exception {
    new App();
  }

  private static BufferedImage createBufferedImage(String fileName) {
    BufferedImage image;
    try {
      image = ImageIO.read(new File(fileName));
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

    int sizeX = image.getWidth();
    int sizeY = image.getHeight();

    BufferedImage result = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
    Graphics g = result.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return result;
  }

  private static BufferedImage createBufferedImage(BufferedImage in) {
    BufferedImage result = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics g = result.createGraphics();
    g.drawImage(in, 0, 0, null);
    g.dispose();
    return result;
  }

  public void initIn(BufferedImage in) {
    int w = in.getWidth() / 2;
    int h = in.getHeight() / 2;
    in1 = createBufferedImage(in.getSubimage(0, 0, w - 1, h - 1));
    in2 = createBufferedImage(in.getSubimage(w, 0, in.getWidth() - w, h - 1));
    in3 = createBufferedImage(in.getSubimage(0, h, w, in.getHeight() - h));
    in4 = createBufferedImage(in.getSubimage(w, h, in.getWidth() - w, in.getHeight() - h));
  }

  public App() throws Exception {
    initIn(Objects.requireNonNull(createBufferedImage("src/main/resources/data/test.jpg")));
    initOut();
    initCL();
    runOpenClProgramWithSource();
  }

  private void initOut() {
    out1 = new BufferedImage(in1.getWidth(), in1.getHeight(), BufferedImage.TYPE_INT_RGB);
    out2 = new BufferedImage(in2.getWidth(), in2.getHeight(), BufferedImage.TYPE_INT_RGB);
    out3 = new BufferedImage(in3.getWidth(), in3.getHeight(), BufferedImage.TYPE_INT_RGB);
    out4 = new BufferedImage(in4.getWidth(), in4.getHeight(), BufferedImage.TYPE_INT_RGB);
  }

  void initCL() {
    final int platformIndex = 0;
    final long deviceType = CL_DEVICE_TYPE_ALL;
    final int deviceIndex = 0;

    CL.setExceptionsEnabled(true);

    int[] numPlatformsArray = new int[1];
    clGetPlatformIDs(0, null, numPlatformsArray);
    int numPlatforms = numPlatformsArray[0];

    cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
    clGetPlatformIDs(platforms.length, platforms, null);
    cl_platform_id platform = platforms[platformIndex];

    cl_context_properties contextProperties = new cl_context_properties();
    contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

    int[] numDevicesArray = new int[1];
    clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
    int numDevices = numDevicesArray[0];

    cl_device_id[] devices = new cl_device_id[numDevices];
    clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
    cl_device_id device = devices[deviceIndex];

    context = clCreateContext(contextProperties, 1, new cl_device_id[]{device}, null, null, null);

    int[] imageSupport = new int[1];
    clGetDeviceInfo(device, CL.CL_DEVICE_IMAGE_SUPPORT, Sizeof.cl_int, Pointer.to(imageSupport), null);
    if (imageSupport[0] == 0) {
      System.out.println("Images are not supported");
      System.exit(1);
      return;
    }

    cl_queue_properties properties = new cl_queue_properties();
    properties.addProperty(CL_QUEUE_PROFILING_ENABLE, 1);
    properties.addProperty(CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, 1);
    commandQueue = clCreateCommandQueueWithProperties(context, device, properties, null);
  }

  private void runOpenClProgramWithSource() throws Exception {
    String programSource = Files.readString(Path.of("src/main/resources/kernels/source1.cl"), StandardCharsets.UTF_8);
    cl_program program = clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);
    clBuildProgram(program, 0, null, null, null, null);

    cl_kernel kernel1 = clCreateKernel(program, "rotateImage", null);
    cl_kernel kernel2 = clCreateKernel(program, "rotateImage", null);
    cl_kernel kernel3 = clCreateKernel(program, "rotateImage", null);
    cl_kernel kernel4 = clCreateKernel(program, "rotateImage", null);

    cl_image_format imageFormat = new cl_image_format();
    imageFormat.image_channel_order = CL_RGBA;
    imageFormat.image_channel_data_type = CL_UNSIGNED_INT8;

    int[] dataIn1 = ((DataBufferInt) in1.getRaster().getDataBuffer()).getData();
    int[] dataIn2 = ((DataBufferInt) in2.getRaster().getDataBuffer()).getData();
    int[] dataIn3 = ((DataBufferInt) in3.getRaster().getDataBuffer()).getData();
    int[] dataIn4 = ((DataBufferInt) in4.getRaster().getDataBuffer()).getData();

    cl_mem inMem1 = clCreateImage2D(context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, new cl_image_format[]{imageFormat}, in1.getWidth(), in1.getHeight(), (long) in1.getWidth() * Sizeof.cl_uint, Pointer.to(dataIn1), null);
    cl_mem outMem1 = clCreateImage2D(context, CL_MEM_WRITE_ONLY, new cl_image_format[]{imageFormat}, in1.getWidth(), in1.getHeight(), 0, null, null);

    cl_mem inMem2 = clCreateImage2D(context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, new cl_image_format[]{imageFormat}, in2.getWidth(), in2.getHeight(), (long) in2.getWidth() * Sizeof.cl_uint, Pointer.to(dataIn2), null);
    cl_mem outMem2 = clCreateImage2D(context, CL_MEM_WRITE_ONLY, new cl_image_format[]{imageFormat}, in2.getWidth(), in2.getHeight(), 0, null, null);

    cl_mem inMem3 = clCreateImage2D(context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, new cl_image_format[]{imageFormat}, in3.getWidth(), in3.getHeight(), (long) in3.getWidth() * Sizeof.cl_uint, Pointer.to(dataIn3), null);
    cl_mem outMem3 = clCreateImage2D(context, CL_MEM_WRITE_ONLY, new cl_image_format[]{imageFormat}, in3.getWidth(), in3.getHeight(), 0, null, null);

    cl_mem inMem4 = clCreateImage2D(context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, new cl_image_format[]{imageFormat}, in4.getWidth(), in4.getHeight(), (long) in4.getWidth() * Sizeof.cl_uint, Pointer.to(dataIn4), null);
    cl_mem outMem4 = clCreateImage2D(context, CL_MEM_WRITE_ONLY, new cl_image_format[]{imageFormat}, in4.getWidth(), in4.getHeight(), 0, null, null);

    cl_event kernelEvent1 = new cl_event();
    cl_event kernelEvent2 = new cl_event();
    cl_event kernelEvent3 = new cl_event();
    cl_event kernelEvent4 = new cl_event();

    clSetKernelArg(kernel1, 0, Sizeof.cl_mem, Pointer.to(inMem1));
    clSetKernelArg(kernel1, 1, Sizeof.cl_mem, Pointer.to(outMem1));
    clSetKernelArg(kernel1, 2, Sizeof.cl_float, Pointer.to(new float[]{0.45f}));

    clSetKernelArg(kernel2, 0, Sizeof.cl_mem, Pointer.to(inMem2));
    clSetKernelArg(kernel2, 1, Sizeof.cl_mem, Pointer.to(outMem2));
    clSetKernelArg(kernel2, 2, Sizeof.cl_float, Pointer.to(new float[]{0.45f}));

    clSetKernelArg(kernel3, 0, Sizeof.cl_mem, Pointer.to(inMem3));
    clSetKernelArg(kernel3, 1, Sizeof.cl_mem, Pointer.to(outMem3));
    clSetKernelArg(kernel3, 2, Sizeof.cl_float, Pointer.to(new float[]{0.45f}));

    clSetKernelArg(kernel4, 0, Sizeof.cl_mem, Pointer.to(inMem4));
    clSetKernelArg(kernel4, 1, Sizeof.cl_mem, Pointer.to(outMem4));
    clSetKernelArg(kernel4, 2, Sizeof.cl_float, Pointer.to(new float[]{0.45f}));

    clEnqueueNDRangeKernel(commandQueue, kernel1, 2, null, new long[]{in1.getWidth(), in1.getHeight()}, null, 0, null, kernelEvent1);
    clEnqueueNDRangeKernel(commandQueue, kernel2, 2, null, new long[]{in2.getWidth(), in2.getHeight()}, null, 0, null, kernelEvent2);
    clEnqueueNDRangeKernel(commandQueue, kernel3, 2, null, new long[]{in3.getWidth(), in3.getHeight()}, null, 0, null, kernelEvent3);
    clEnqueueNDRangeKernel(commandQueue, kernel4, 2, null, new long[]{in4.getWidth(), in4.getHeight()}, null, 0, null, kernelEvent4);

    clWaitForEvents(4, new cl_event[]{kernelEvent1, kernelEvent2, kernelEvent3, kernelEvent4});

    cl_event readEvent1 = new cl_event();
    cl_event readEvent2 = new cl_event();
    cl_event readEvent3 = new cl_event();
    cl_event readEvent4 = new cl_event();

    int[] dataOut1 = ((DataBufferInt) out1.getRaster().getDataBuffer()).getData();
    int[] dataOut2 = ((DataBufferInt) out2.getRaster().getDataBuffer()).getData();
    int[] dataOut3 = ((DataBufferInt) out3.getRaster().getDataBuffer()).getData();
    int[] dataOut4 = ((DataBufferInt) out4.getRaster().getDataBuffer()).getData();

    clEnqueueReadImage(commandQueue, outMem1, true, new long[3], new long[]{in1.getWidth(), in1.getHeight(), 1}, (long) in1.getWidth() * Sizeof.cl_uint, 0, Pointer.to(dataOut1), 0, null, readEvent1);
    clEnqueueReadImage(commandQueue, outMem2, true, new long[3], new long[]{in2.getWidth(), in2.getHeight(), 1}, (long) in2.getWidth() * Sizeof.cl_uint, 0, Pointer.to(dataOut2), 0, null, readEvent2);
    clEnqueueReadImage(commandQueue, outMem3, true, new long[3], new long[]{in3.getWidth(), in3.getHeight(), 1}, (long) in3.getWidth() * Sizeof.cl_uint, 0, Pointer.to(dataOut3), 0, null, readEvent3);
    clEnqueueReadImage(commandQueue, outMem4, true, new long[3], new long[]{in4.getWidth(), in4.getHeight(), 1}, (long) in4.getWidth() * Sizeof.cl_uint, 0, Pointer.to(dataOut4), 0, null, readEvent4);

    clWaitForEvents(4, new cl_event[]{readEvent1, readEvent2, readEvent3, readEvent4});

    ImageIO.write(out1, "png", new File("results/out1.png"));
    ImageIO.write(out2, "png", new File("results/out2.png"));
    ImageIO.write(out3, "png", new File("results/out3.png"));
    ImageIO.write(out4, "png", new File("results/out4.png"));
  }
}

