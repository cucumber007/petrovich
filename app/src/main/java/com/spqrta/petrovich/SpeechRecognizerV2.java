package com.spqrta.petrovich;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.Looper;

import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpkModel;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

/**
 * Main class to access recognizer functions. After configuration this class
 * starts a listener thread which records the data and recognizes it using
 * VOSK engine. Recognition events are passed to a client using
 * {@link RecognitionListener}
 */
public class SpeechRecognizerV2 {

  protected static final String TAG = SpeechRecognizerV2.class.getSimpleName();

  private final KaldiRecognizer recognizer;

  private final int sampleRate;
  private final static float BUFFER_SIZE_SECONDS = 0.4f;
  private int bufferSize;
  private final AudioRecord recorder;

  private Thread recognizerThread;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private final Collection<RecognitionListener> listeners = new HashSet<RecognitionListener>();

  /**
   * Creates speech recognizer. Recognizer holds the AudioRecord object, so you
   * need to call {@link release} in order to properly finalize it.
   *
   * @throws IOException thrown if audio recorder can not be created for some reason.
   */
  public SpeechRecognizerV2(Model model, String grammar) throws IOException {
    recognizer = new KaldiRecognizer(model, 16000.0f, grammar);
    sampleRate = 16000;
    bufferSize = Math.round(sampleRate * BUFFER_SIZE_SECONDS);
    recorder =
        new AudioRecord(AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);

    if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
      recorder.release();
      throw new IOException("Failed to initialize recorder. Microphone might be already in use.");
    }
  }

  public SpeechRecognizerV2(Model model, SpkModel spkModel) throws IOException {
    recognizer = new KaldiRecognizer(model, spkModel, 16000.0f);
    sampleRate = 16000;
    bufferSize = Math.round(sampleRate * BUFFER_SIZE_SECONDS);
    recorder =
        new AudioRecord(AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);

    if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
      recorder.release();
      throw new IOException("Failed to initialize recorder. Microphone might be already in use.");
    }
  }

  /**
   * Adds listener.
   */
  public void addListener(RecognitionListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  /**
   * Removes listener.
   */
  public void removeListener(RecognitionListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  /**
   * Starts recognition. Does nothing if recognition is active.
   *
   * @return true if recognition was actually started
   */
  public boolean startListening() {
    if (null != recognizerThread) return false;

    recognizerThread = new RecognizerThread();
    recognizerThread.start();
    return true;
  }

  /**
   * Starts recognition. After specified timeout listening stops and the
   * endOfSpeech signals about that. Does nothing if recognition is active.
   *
   * @return true if recognition was actually started
   * @timeout - timeout in milliseconds to listen.
   */
  public boolean startListening(int timeout) {
    if (null != recognizerThread) return false;

    recognizerThread = new RecognizerThread(timeout);
    recognizerThread.start();
    return true;
  }

  private boolean stopRecognizerThread() {
    if (null == recognizerThread) return false;

    try {
      recognizerThread.interrupt();
      recognizerThread.join();
    } catch (InterruptedException e) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }

    recognizerThread = null;
    return true;
  }

  /**
   * Stops recognition. All listeners should receive final result if there is
   * any. Does nothing if recognition is not active.
   *
   * @return true if recognition was actually stopped
   */
  public boolean stop() {
    boolean result = stopRecognizerThread();
    if (result) {
      mainHandler.post(new ResultEvent(recognizer.Result(), true));
    }
    return result;
  }

  /**
   * Cancels recognition. Listeners do not receive final result. Does nothing
   * if recognition is not active.
   *
   * @return true if recognition was actually canceled
   */
  public boolean cancel() {
    boolean result = stopRecognizerThread();
    recognizer.Result(); // Reset recognizer state
    return result;
  }

  /**
   * Shutdown the recognizer and release the recorder
   */
  public void shutdown() {
    recorder.release();
  }

  private final class RecognizerThread extends Thread {

    private int remainingSamples;
    private int timeoutSamples;
    private final static int NO_TIMEOUT = -1;

    public RecognizerThread(int timeout) {
      if (timeout != NO_TIMEOUT) {
        this.timeoutSamples = timeout * sampleRate / 1000;
      } else {
        this.timeoutSamples = NO_TIMEOUT;
      }
      this.remainingSamples = this.timeoutSamples;
    }

    public RecognizerThread() {
      this(NO_TIMEOUT);
    }

    @Override public void run() {

      recorder.startRecording();
      if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
        recorder.stop();
        IOException ioe =
            new IOException("Failed to start recording. Microphone might be already in use.");
        mainHandler.post(new OnErrorEvent(ioe));
        return;
      }

      short[] buffer = new short[bufferSize];

      while (!interrupted() && ((timeoutSamples == NO_TIMEOUT) || (remainingSamples > 0))) {
        int nread = recorder.read(buffer, 0, buffer.length);

        if (nread < 0) {
          throw new RuntimeException("error reading audio buffer");
        } else {
          boolean isFinal = recognizer.AcceptWaveform(buffer, nread);
          if (isFinal) {
            mainHandler.post(new ResultEvent(recognizer.Result(), true));
          } else {
            mainHandler.post(new ResultEvent(recognizer.PartialResult(), false));
          }
        }

        if (timeoutSamples != NO_TIMEOUT) {
          remainingSamples = remainingSamples - nread;
        }
      }

      recorder.stop();

      // Remove all pending notifications.
      mainHandler.removeCallbacksAndMessages(null);

      // If we met timeout signal that speech ended
      if (timeoutSamples != NO_TIMEOUT && remainingSamples <= 0) {
        mainHandler.post(new TimeoutEvent());
      }
    }
  }

  private abstract class RecognitionEvent implements Runnable {
    public void run() {
      RecognitionListener[] emptyArray = new RecognitionListener[0];
      for (RecognitionListener listener : listeners.toArray(emptyArray))
        execute(listener);
    }

    protected abstract void execute(RecognitionListener listener);
  }

  private class ResultEvent extends RecognitionEvent {
    protected final String hypothesis;
    private final boolean finalResult;

    ResultEvent(String hypothesis, boolean finalResult) {
      this.hypothesis = hypothesis;
      this.finalResult = finalResult;
    }

    @Override protected void execute(RecognitionListener listener) {
      if (finalResult) {
        listener.onResult(hypothesis);
      } else {
        listener.onPartialResult(hypothesis);
      }
    }
  }

  private class OnErrorEvent extends RecognitionEvent {
    private final Exception exception;

    OnErrorEvent(Exception exception) {
      this.exception = exception;
    }

    @Override protected void execute(RecognitionListener listener) {
      listener.onError(exception);
    }
  }

  private class TimeoutEvent extends RecognitionEvent {
    @Override protected void execute(RecognitionListener listener) {
      listener.onTimeout();
    }
  }
}
