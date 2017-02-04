package com.codebrig.jnomad.task.explain.transform.hql

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLTransformException extends Exception {

    HQLTransformException(String message) {
        super(message)
    }

    HQLTransformException(String message, Throwable cause) {
        super(message, cause)
    }

    HQLTransformException(Throwable cause) {
        super(cause)
    }

}
