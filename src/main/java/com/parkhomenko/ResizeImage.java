package com.parkhomenko;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.parser.PdfImageObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class ResizeImage {
    private static final long TWO_MB_IN_BYTES = 2_000_000;
    private static final float FACTOR = 0.5f;

    public static void main(String[] args) throws IOException, DocumentException {
        final var src = "21898585_FR_Ops_General.pdf";
        final var compressedFilePath = new ResizeImage()
                .manipulatePdf(src, 0, src);

        System.out.println("compressedFilePath = " + compressedFilePath);
    }

    public String manipulatePdf(String currentPath, int step, String originPath) throws IOException, DocumentException {
        if (fileSize(currentPath) < TWO_MB_IN_BYTES) {
            return currentPath;
        }

        var destPath = getCompressedFileName(originPath, step);
        compress(currentPath, destPath);
        deleteTmpFile(currentPath, step);
        return manipulatePdf(destPath, ++step, originPath);
    }

    private String getCompressedFileName(String currentPath, int step) {
        var baseName = FilenameUtils.getBaseName(currentPath);
        return baseName + "_compressed_by_step_" + step + ".pdf";
    }

    private void deleteTmpFile(String currentPath, int step) {
        if (step > 0) {
            var fileToDelete = FileUtils.getFile(currentPath);
            FileUtils.deleteQuietly(fileToDelete);
        }
    }

    private long fileSize(String filePath) {
        return FileUtils.sizeOf(new File(filePath));
    }

    private void compress(String src, String dest) throws IOException, DocumentException {
        // Read the file
        PdfReader reader = new PdfReader(src);
        int n = reader.getXrefSize();
        PdfObject object;
        PRStream stream;
        // Look for image and manipulate image stream
        for (int i = 0; i < n; i++) {
            object = reader.getPdfObject(i);

            if (object == null || !object.isStream()) {
                continue;
            }
            stream = (PRStream)object;

            PdfObject pdfSubtype = stream.get(PdfName.SUBTYPE);

            if (pdfSubtype != null && pdfSubtype.toString().equals(PdfName.IMAGE.toString())) {
                PdfImageObject image = new PdfImageObject(stream);
                BufferedImage bi = image.getBufferedImage();
                if (bi == null) continue;
                int width = (int)(bi.getWidth() * FACTOR);
                int height = (int)(bi.getHeight() * FACTOR);
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                AffineTransform at = AffineTransform.getScaleInstance(FACTOR, FACTOR);
                Graphics2D g = img.createGraphics();
                g.drawRenderedImage(bi, at);
                ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
                ImageIO.write(img, "JPG", imgBytes);
                stream.clear();
                stream.setData(imgBytes.toByteArray(), false, PRStream.BEST_COMPRESSION);
                stream.put(PdfName.TYPE, PdfName.XOBJECT);
                stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
                stream.put(PdfName.FILTER, PdfName.DCTDECODE);
                stream.put(PdfName.WIDTH, new PdfNumber(width));
                stream.put(PdfName.HEIGHT, new PdfNumber(height));
                stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
                stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);
            }
        }

        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(dest));
        stamper.close();
        reader.close();
    }
}
